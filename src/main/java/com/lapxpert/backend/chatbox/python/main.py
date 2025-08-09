"""
LapXpert AI Chat Service - FastAPI Backend (Streamlined Architecture)
Provides AI chat functionality with Vietnamese language support and product recommendations
Integrates with GitHub AI (Mistral Medium 3) for enhanced conversational AI

STREAMLINED FEATURES:
- Unified hybrid approach: /chat/recommend handles all chat types
- No intent classification: AI model decides response type based on context
- Vietnamese-first processing with pyvi tokenization
- Vector similarity search for product recommendations
- Real-time streaming responses via WebSocket integration
- 180-second timeout compatibility for complex AI processing
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import logging
import asyncio
import json
import time
import asyncpg
from datetime import datetime

# Handle optional dependencies gracefully
try:
    import asyncpg
    ASYNCPG_AVAILABLE = True
except ImportError:
    ASYNCPG_AVAILABLE = False
    print("⚠️ Warning: asyncpg not available. Database functionality will be limited.")
    print("   Install with: pip install asyncpg")

from vietnamese_embedding_service import VietnameseEmbeddingService
from github_ai_service import GitHubAIService
from chat_models import ChatRequest, ProductRecommendation, ChatResponse, ConversationalRequest, \
    ConversationalResponse, SearchRequest
from config import DB_CONFIG, MODEL_NAME, TABLE_NAME, EMBEDDING_DIM, TOP_K

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ================== Configuration imported from config.py ==================
# All configuration constants now centralized in config.py module

# Update model defaults after TOP_K is defined (Pydantic V2 compatible)
SearchRequest.model_fields['top_k'].default = TOP_K
ChatRequest.model_fields['top_k'].default = TOP_K

# ================== Tải services ==================
logger.info("🔍 Loading Vietnamese embedding service...")
embedding_service = VietnameseEmbeddingService(MODEL_NAME)
logger.info("✅ Vietnamese embedding service loaded.")

logger.info("🔍 Initializing GitHub AI service...")
ai_service = GitHubAIService()
logger.info("✅ GitHub AI service initialized.")



# ================== App FastAPI ==================
from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    global db_pool

    logger.info("🚀 LapXpert AI Chat API starting up...")
    logger.info(f"📊 Using Vietnamese embedding model: {MODEL_NAME}")

    # Initialize database connection pool
    if ASYNCPG_AVAILABLE:
        db_pool = await asyncpg.create_pool(
            host=DB_CONFIG['host'],
            port=DB_CONFIG['port'],
            user=DB_CONFIG['user'],
            password=DB_CONFIG['password'],
            database=DB_CONFIG['dbname'],
            min_size=5,
            max_size=20
        )
        logger.info("✅ Database connection pool initialized")
    else:
        db_pool = None
        logger.warning("⚠️ Database connection pool not initialized (asyncpg not available)")

    # Check GitHub AI service connection
    if ai_service.check_connection():
        logger.info("✅ GitHub AI service connected successfully")
        if ai_service.check_model_availability():
            logger.info(f"✅ Model {ai_service.model} is ready")
        else:
            logger.warning("⚠️ GitHub AI model not available, using fallback responses")
    else:
        logger.warning("⚠️ GitHub AI service not available, using fallback responses")

    logger.info("🎉 LapXpert AI Chat API ready!")

    # Test the enhanced parser function with actual data format
    logger.info("🧪 Testing enhanced parse_product_from_full_text function...")
    test_parse_product_from_full_text()

    yield

    # Shutdown
    if db_pool:
        await db_pool.close()
        logger.info("✅ Database connections closed")

app = FastAPI(
    title="LapXpert AI Chat API",
    description="Enhanced AI chat with Vietnamese product recommendations",
    version="2.0.0",
    lifespan=lifespan
)

# ================== Database Pool ==================
db_pool = None

# ================== HELPER FUNCTIONS ==================







# ================== Helper Functions ==================
def parse_product_from_full_text(full_text: str, san_pham_chi_tiet_id: int) -> dict:
    """
    Robust parser for structured product information from SAN_PHAM_EMBEDDINGS.full_text field

    Expected format: 'Tên sản phẩm: X - Thương hiệu: Y - CPU: Z - RAM: A - GPU: B - Màn hình: C - Bộ nhớ: D - Màu sắc: E - Giá bán: N'

    Args:
        full_text: Structured text containing product information
        san_pham_chi_tiet_id: Product variant ID for logging

    Returns:
        dict: Parsed product data with ten_san_pham, gia_ban, mo_ta fields
    """
    import re

    try:
        if not full_text or not isinstance(full_text, str):
            logger.warning(f"Dữ liệu full_text không hợp lệ cho sản phẩm {san_pham_chi_tiet_id}: {full_text}")
            return {
                "san_pham_chi_tiet_id": san_pham_chi_tiet_id,
                "ten_san_pham": "Không có tên",
                "gia_ban": 0.0,
                "mo_ta": "Không có thông tin kỹ thuật"
            }

        # Initialize default values
        ten_san_pham = "Không có tên"
        gia_ban = 0.0
        technical_specs = {}

        # Regex patterns for extracting specific fields
        ten_san_pham_pattern = r'Tên sản phẩm:\s*([^-]+)'
        gia_ban_pattern = r'Giá bán:\s*([\d\.]+)'
        specs_pattern = r'(CPU|RAM|GPU|Màn hình|Bộ nhớ|Màu sắc|Thương hiệu):\s*([^-]+)'

        # Extract product name
        ten_san_pham_match = re.search(ten_san_pham_pattern, full_text)
        if ten_san_pham_match:
            ten_san_pham = ten_san_pham_match.group(1).strip()
            if not ten_san_pham:
                ten_san_pham = "Không có tên"

        # Extract price with robust error handling
        gia_ban_match = re.search(gia_ban_pattern, full_text)
        if gia_ban_match:
            try:
                price_str = gia_ban_match.group(1).strip()
                # Handle Vietnamese decimal formatting and remove any trailing spaces
                price_str = price_str.replace(',', '').replace(' ', '')
                gia_ban = float(price_str) if price_str else 0.0
            except (ValueError, TypeError) as e:
                logger.warning(f"Không thể chuyển đổi giá '{gia_ban_match.group(1)}' cho sản phẩm {san_pham_chi_tiet_id}: {e}")
                gia_ban = 0.0

        # Extract technical specifications
        specs_matches = re.findall(specs_pattern, full_text)
        for spec_name, spec_value in specs_matches:
            spec_value = spec_value.strip()
            if spec_value and spec_value.lower() not in ["không có", "n/a", ""]:
                technical_specs[spec_name] = spec_value

        # Generate comprehensive mo_ta from technical specifications
        if technical_specs:
            # Order specifications for better readability
            spec_order = ["CPU", "RAM", "GPU", "Màn hình", "Bộ nhớ", "Màu sắc", "Thương hiệu"]
            ordered_specs = []

            for spec_name in spec_order:
                if spec_name in technical_specs:
                    ordered_specs.append(f"{spec_name}: {technical_specs[spec_name]}")

            # Add any remaining specs not in the ordered list
            for spec_name, spec_value in technical_specs.items():
                if spec_name not in spec_order:
                    ordered_specs.append(f"{spec_name}: {spec_value}")

            mo_ta = ", ".join(ordered_specs)
        else:
            mo_ta = "Thông tin kỹ thuật không đầy đủ"

        logger.debug(f"Đã phân tích thành công sản phẩm {san_pham_chi_tiet_id}: {ten_san_pham}")

        return {
            "san_pham_chi_tiet_id": san_pham_chi_tiet_id,
            "ten_san_pham": ten_san_pham,
            "gia_ban": gia_ban,
            "mo_ta": mo_ta
        }

    except Exception as e:
        logger.error(f"Lỗi phân tích full_text cho sản phẩm {san_pham_chi_tiet_id}: {e}")
        return {
            "san_pham_chi_tiet_id": san_pham_chi_tiet_id,
            "ten_san_pham": "Lỗi xử lý dữ liệu",
            "gia_ban": 0.0,
            "mo_ta": "Không thể xử lý thông tin sản phẩm"
        }

def test_parse_product_from_full_text():
    """
    Test function to verify parse_product_from_full_text works with actual data format
    """
    # Test with actual sample data from SAN_PHAM_EMBEDDINGS
    sample_full_text = "Tên sản phẩm: poooo - Thương hiệu: MSI - CPU: Intel Core i5 15400K - RAM: 16 GB DDR5 - GPU: RTX 5090 - Màn hình: 17\" 4K - Bộ nhớ: 512 GB SSD - Màu sắc: Đỏ - Giá bán: 600000.00 "

    result = parse_product_from_full_text(sample_full_text, 7)

    logger.info("🧪 Testing parse_product_from_full_text function:")
    logger.info(f"📝 Input: {sample_full_text}")
    logger.info(f"✅ Parsed result:")
    logger.info(f"   - ID: {result['san_pham_chi_tiet_id']}")
    logger.info(f"   - Tên sản phẩm: {result['ten_san_pham']}")
    logger.info(f"   - Giá bán: {result['gia_ban']}")
    logger.info(f"   - Mô tả: {result['mo_ta']}")

    # Test edge cases
    logger.info("\n🧪 Testing edge cases:")

    # Test with empty string
    empty_result = parse_product_from_full_text("", 999)
    logger.info(f"📝 Empty string result: {empty_result['ten_san_pham']}")

    # Test with malformed price
    malformed_price_text = "Tên sản phẩm: Test Product - Giá bán: invalid_price"
    malformed_result = parse_product_from_full_text(malformed_price_text, 998)
    logger.info(f"📝 Malformed price result: {malformed_result['gia_ban']}")

    return result

# ================== API Endpoints ==================

@app.post("/chat/recommend", response_model=ChatResponse)
async def chat_recommend(req: ChatRequest):
    """Enhanced AI chat with Vietnamese product recommendations"""
    try:
        logger.info(f"Processing chat request: {req.message}")

        # 1. Process Vietnamese text and generate embedding
        query_embed = embedding_service.encode_text(req.message).tolist()

        # 2. Find similar products using vector search
        if not ASYNCPG_AVAILABLE or not db_pool:
            logger.warning("Database not available, using mock search results")
            search_results = [
                {
                    'san_pham_chi_tiet_id': 1,
                    'distance': 0.1,
                    'full_text': 'Tên sản phẩm: Laptop Demo 1 - Thương hiệu: Demo Brand - CPU: Intel Core i5 - RAM: 8 GB DDR4 - GPU: Intel UHD - Màn hình: 15" FHD - Bộ nhớ: 256 GB SSD - Màu sắc: Đen - Giá bán: 15000000.00'
                },
                {
                    'san_pham_chi_tiet_id': 2,
                    'distance': 0.2,
                    'full_text': 'Tên sản phẩm: Laptop Demo 2 - Thương hiệu: Demo Brand - CPU: Intel Core i7 - RAM: 16 GB DDR4 - GPU: NVIDIA GTX 1650 - Màn hình: 15" FHD - Bộ nhớ: 512 GB SSD - Màu sắc: Bạc - Giá bán: 20000000.00'
                },
                {
                    'san_pham_chi_tiet_id': 3,
                    'distance': 0.3,
                    'full_text': 'Tên sản phẩm: Laptop Demo 3 - Thương hiệu: Demo Brand - CPU: AMD Ryzen 5 - RAM: 8 GB DDR4 - GPU: AMD Radeon - Màn hình: 14" FHD - Bộ nhớ: 256 GB SSD - Màu sắc: Xanh - Giá bán: 18000000.00'
                }
            ]
        else:
            async with db_pool.acquire() as conn:
                query = f"""
                    SELECT san_pham_chi_tiet_id, full_text,
                        embedding <#> $1::vector AS distance
                    FROM {TABLE_NAME}
                    ORDER BY distance ASC
                    LIMIT $2
                """
                # Convert list to PostgreSQL vector format
                vector_str = '[' + ','.join(map(str, query_embed)) + ']'
                search_results = await conn.fetch(query, vector_str, req.top_k)

        # 3. Parse product details directly from search results (no additional database queries)
        product_details = []
        recommendations = []

        for result in search_results:
            san_pham_chi_tiet_id = result['san_pham_chi_tiet_id']
            full_text = result.get('full_text', '')
            distance = result['distance']

            # Parse product information from full_text
            product = parse_product_from_full_text(full_text, san_pham_chi_tiet_id)
            product_details.append(product)

            # Create recommendation with similarity score
            similarity_score = round(1 - distance, 4)  # Convert distance to similarity
            recommendations.append(ProductRecommendation(
                san_pham_chi_tiet_id=san_pham_chi_tiet_id,
                ten_san_pham=product["ten_san_pham"],
                gia_ban=product["gia_ban"],
                mo_ta=product["mo_ta"],
                similarity_score=similarity_score
            ))

        # 6. Generate AI response using GitHub AI
        ai_response = ""
        ai_available = ai_service.check_connection()

        if ai_available and product_details:
            try:
                ai_response = ai_service.generate_product_recommendation(
                    req.message, product_details
                )
            except Exception as e:
                logger.error(f"GitHub AI generation failed: {e}")
                ai_response = None

        # 7. Fallback response if GitHub AI fails
        if not ai_response:
            ai_response = ai_service.get_fallback_response(req.message)

        return ChatResponse(
            ai_response=ai_response,
            product_recommendations=recommendations,
            query_processed=embedding_service.preprocess_vietnamese_text(req.message),
            ai_available=ai_available  # Updated to reflect GitHub AI usage
        )

    except Exception as e:
        logger.error(f"Error in chat recommendation: {e}")
        raise HTTPException(status_code=500, detail=str(e))



@app.post("/chat/conversational", response_model=ConversationalResponse)
async def chat_conversational(req: ConversationalRequest):
    """Pure conversational AI without product recommendations"""
    try:
        logger.info(f"Processing conversational request: {req.message}")

        # Process Vietnamese text for transparency
        processed_text = embedding_service.preprocess_vietnamese_text(req.message)

        # Generate conversational AI response using GitHub AI
        ai_response = ""
        ai_available = ai_service.check_connection()

        if ai_available:
            try:
                ai_response = ai_service.generate_general_chat_response(req.message)
                logger.info("✅ Conversational response generated successfully")
            except Exception as e:
                logger.error(f"GitHub AI conversational generation failed: {e}")
                ai_response = None

        # Fallback response if GitHub AI fails
        if not ai_response:
            ai_response = ai_service.get_fallback_response(req.message)
            logger.info("Using fallback response for conversational request")

        return ConversationalResponse(
            ai_response=ai_response,
            query_processed=processed_text,
            ai_available=ai_available  # Updated to reflect GitHub AI usage
        )

    except Exception as e:
        logger.error(f"Error in conversational chat: {e}")
        raise HTTPException(status_code=500, detail=f"Conversational chat failed: {str(e)}")

@app.get("/health")
async def health_check():
    """
    Health check endpoint for service readiness verification
    Returns service status and GitHub AI connectivity
    """
    try:
        # Check GitHub AI service connectivity
        ai_available = ai_service.check_connection()

        # Check if model is available
        model_available = False
        if ai_available:
            model_available = ai_service.check_model_availability()

        # Determine overall health status
        status = "healthy" if ai_available and model_available else "degraded"

        return {
            "status": status,
            "ai_service": {
                "available": ai_available,
                "model_available": model_available,
                "model": ai_service.model if ai_available else None
            },
            "database": {
                "available": ASYNCPG_AVAILABLE and db_pool is not None
            },
            "timestamp": datetime.now().isoformat()
        }
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return {
            "status": "unhealthy",
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        }


# ================== Startup Events ==================
# Note: Startup and shutdown events moved to lifespan context manager above





# ================== Main ==================
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8001,
        reload=True,
        log_level="info"
    )
