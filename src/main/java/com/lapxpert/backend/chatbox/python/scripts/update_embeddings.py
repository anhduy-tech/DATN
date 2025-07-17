import psycopg2
from sentence_transformers import SentenceTransformer
from tqdm import tqdm
import logging
import sys
import os

# Add parent directory to path to import from root chatbox directory
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from vietnamese_embedding_service import VietnameseEmbeddingService
from config import DB_CONFIG, MODEL_NAME, TABLE_NAME, EMBEDDING_DIM

# ======================= CONFIG =======================
# Configuration now imported from centralized config.py module
VIEW_NAME = "san_pham_embed_view"  # View chứa dữ liệu gốc

# ======================= LOGGING SETUP =======================
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# preprocess_vietnamese_text function now provided by VietnameseEmbeddingService

# ======================= TẢI VIETNAMESE EMBEDDING SERVICE =======================
logger.info(f"⏳ Đang tải Vietnamese embedding service: {MODEL_NAME}")

try:
    embedding_service = VietnameseEmbeddingService(MODEL_NAME)
    logger.info(f"✅ Đã tải thành công Vietnamese embedding service: {MODEL_NAME}")
    logger.info(f"📏 Embedding dimension: {embedding_service.model.get_sentence_embedding_dimension()}")

    # Verify model dimension compatibility
    actual_dim = embedding_service.model.get_sentence_embedding_dimension()
    if actual_dim != EMBEDDING_DIM:
        logger.warning(f"⚠️ Model dimension mismatch: expected {EMBEDDING_DIM}, got {actual_dim}")
        logger.info("🔄 Using actual model dimension...")
        EMBEDDING_DIM = actual_dim

except Exception as e:
    logger.error(f"❌ Lỗi khi tải Vietnamese embedding service: {e}")
    raise

# ======================= KẾT NỐI DB =======================
logger.info("🔌 Đang kết nối đến database...")
try:
    conn = psycopg2.connect(**DB_CONFIG)
    cursor = conn.cursor()
    logger.info("✅ Kết nối database thành công")
except Exception as e:
    logger.error(f"❌ Lỗi kết nối database: {e}")
    raise

# ======================= TẠO BẢNG VECTOR =======================
logger.info(f"📋 Đang tạo/kiểm tra bảng {TABLE_NAME}...")
try:
    cursor.execute(f"""
    CREATE TABLE IF NOT EXISTS {TABLE_NAME} (
        san_pham_chi_tiet_id BIGINT PRIMARY KEY,
        full_text TEXT,
        embedding VECTOR({EMBEDDING_DIM}),
        model_name TEXT DEFAULT '{MODEL_NAME}',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)

    # Add index for better performance if not exists
    cursor.execute(f"""
    CREATE INDEX IF NOT EXISTS idx_{TABLE_NAME}_embedding_cosine
    ON {TABLE_NAME} USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100)
    """)

    conn.commit()
    logger.info(f"✅ Bảng {TABLE_NAME} đã sẵn sàng với index tối ưu")
except Exception as e:
    logger.error(f"❌ Lỗi tạo bảng: {e}")
    raise

# ======================= TRUY VẤN VIEW =======================
logger.info(f"📊 Đang truy vấn dữ liệu từ view {VIEW_NAME}...")
try:
    cursor.execute(f"SELECT san_pham_chi_tiet_id, noi_dung_embed FROM {VIEW_NAME} WHERE noi_dung_embed IS NOT NULL")
    rows = cursor.fetchall()
    logger.info(f"📋 Tìm thấy {len(rows)} bản ghi cần xử lý")
except Exception as e:
    logger.error(f"❌ Lỗi truy vấn view: {e}")
    raise

# ======================= XỬ LÝ VÀ GHI VÀO DB =======================
insert_sql = f"""
INSERT INTO {TABLE_NAME} (san_pham_chi_tiet_id, full_text, embedding, model_name, updated_at)
VALUES (%s, %s, %s, %s, CURRENT_TIMESTAMP)
ON CONFLICT (san_pham_chi_tiet_id) DO UPDATE
SET embedding = EXCLUDED.embedding,
    full_text = EXCLUDED.full_text,
    model_name = EXCLUDED.model_name,
    updated_at = CURRENT_TIMESTAMP
"""

logger.info("🚀 Bắt đầu insert dữ liệu vào bảng embeddings...")

for row in tqdm(rows, desc="Embedding and inserting", unit="record"):
    try:
        san_pham_id, full_text = row
        preprocessed_text = embedding_service.preprocess_vietnamese_text(full_text)
        embedding_vector = embedding_service.model.encode(preprocessed_text).tolist()

        cursor.execute(insert_sql, (
            san_pham_id,
            full_text,
            embedding_vector,
            MODEL_NAME
        ))
    except Exception as e:
        logger.warning(f"⚠️ Lỗi xử lý ID {row[0]}: {e}")

# Sau vòng lặp:
conn.commit()
logger.info("✅ Đã insert xong toàn bộ embeddings.")

# ======================= KIỂM TRA DỮ LIỆU HIỆN TẠI =======================
logger.info(f"📊 Kiểm tra dữ liệu hiện tại trong bảng {TABLE_NAME}...")
try:
    cursor.execute(f"SELECT COUNT(*) FROM {TABLE_NAME}")
    existing_count = cursor.fetchone()[0]
    logger.info(f"📋 Hiện có {existing_count} embeddings trong database")

    if existing_count > 0:
        logger.info("✅ Giữ nguyên embeddings hiện tại để đảm bảo backward compatibility")
        logger.info("🎯 Model mới sẽ được sử dụng cho AI chat queries (real-time embedding)")
    else:
        logger.info("📝 Database trống, có thể tạo embeddings mới nếu cần")

except Exception as e:
    logger.warning(f"⚠️ Không thể kiểm tra dữ liệu hiện tại: {e}")

# ======================= TEST MODEL MỚI =======================
logger.info("🧪 Testing Vietnamese model với pyvi tokenization...")
test_texts = [
    "Laptop gaming ASUS ROG với CPU Intel Core i7",
    "Điện thoại iPhone 15 Pro Max màu xanh",
    "Tai nghe không dây AirPods Pro"
]

logger.info("📝 Test embedding generation:")
for i, text in enumerate(test_texts, 1):
    try:
        # Apply Vietnamese preprocessing
        preprocessed = embedding_service.preprocess_vietnamese_text(text)

        # Generate embedding
        embedding = embedding_service.model.encode(preprocessed)

        logger.info(f"✅ Test {i}: '{text}' -> embedding shape: {embedding.shape}")
        logger.info(f"   Preprocessed: '{preprocessed}'")

    except Exception as e:
        logger.error(f"❌ Test {i} failed: {e}")

# ======================= ĐÓNG KẾT NỐI =======================
cursor.close()
conn.close()

logger.info("🎉 Hoàn thành!")
