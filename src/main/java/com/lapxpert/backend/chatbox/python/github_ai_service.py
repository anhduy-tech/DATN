"""
GitHub AI Service Integration for LapXpert AI Chat
Provides Vietnamese product recommendations using GitHub AI Mistral Medium 3 (25.05) model
"""

import logging
import asyncio
import time
import aiohttp
from typing import List, Dict, Any, Optional
from azure.ai.inference import ChatCompletionsClient
from azure.core.credentials import AzureKeyCredential
from azure.ai.inference.models import SystemMessage, UserMessage
from azure.core.exceptions import HttpResponseError, ServiceRequestError, ClientAuthenticationError
import sys
import os

# Add parent directory to path for relative imports
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from dotenv import load_dotenv
load_dotenv()

from prompt_templates import VietnamesePromptTemplates

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class GitHubAIService:
    """
    GitHub AI service for generating Vietnamese product recommendations using Mistral Medium 3 (25.05)
    """
    
    def __init__(self,
                 github_token: str = os.getenv("GITHUB_AI_TOKEN"),
                 endpoint: str = os.getenv("GITHUB_AI_ENDPOINT", "https://models.github.ai/inference"),
                 model: str = os.getenv("GITHUB_AI_MODEL", "mistral-ai/mistral-medium-2505"),
                 timeout: int = int(os.getenv("GITHUB_AI_TIMEOUT", 180)),
                 max_retries: int = int(os.getenv("GITHUB_AI_MAX_RETRIES", 3)),
                 initial_retry_delay: float = float(os.getenv("GITHUB_AI_INITIAL_RETRY_DELAY", 1.0)),
                 max_retry_delay: float = float(os.getenv("GITHUB_AI_MAX_RETRY_DELAY", 60.0)),
                 backoff_multiplier: float = float(os.getenv("GITHUB_AI_BACKOFF_MULTIPLIER", 2.0))):
        """
        Initialize GitHub AI service with enhanced error handling

        Args:
            github_token: GitHub token for authentication
            endpoint: GitHub AI inference endpoint
            model: Model name to use
            timeout: Request timeout in seconds (compatible with Java WebClient 180s)
            max_retries: Maximum number of retry attempts
            initial_retry_delay: Initial delay between retries in seconds
            max_retry_delay: Maximum delay between retries in seconds
            backoff_multiplier: Exponential backoff multiplier
        """
        self.endpoint = endpoint
        self.model = model
        self.timeout = timeout
        self.github_token = github_token
        self.max_retries = max_retries
        self.initial_retry_delay = initial_retry_delay
        self.max_retry_delay = max_retry_delay
        self.backoff_multiplier = backoff_multiplier



        self.client = None
        self.prompt_templates = VietnamesePromptTemplates()
        self._initialize_clients()
    
    def _initialize_clients(self):
        """Initialize GitHub AI client"""
        try:
            # Initialize synchronous client
            self.client = ChatCompletionsClient(
                endpoint=self.endpoint,
                credential=AzureKeyCredential(self.github_token)
            )

            logger.info(f"✅ GitHub AI client initialized for endpoint: {self.endpoint}")
        except Exception as e:
            logger.error(f"❌ Failed to initialize GitHub AI client: {e}")
            self.client = None



    def _calculate_retry_delay(self, attempt: int) -> float:
        """Calculate exponential backoff delay"""
        delay = self.initial_retry_delay * (self.backoff_multiplier ** attempt)
        return min(delay, self.max_retry_delay)

    def _is_retryable_error(self, error: Exception) -> bool:
        """Determine if an error is retryable"""
        if isinstance(error, HttpResponseError):
            # Retry on server errors (5xx) and rate limiting (429)
            if error.status_code in [429, 500, 502, 503, 504]:
                return True
            # Don't retry on client errors (4xx) except rate limiting
            if 400 <= error.status_code < 500:
                return False

        if isinstance(error, (ServiceRequestError, ConnectionError, TimeoutError)):
            return True

        if isinstance(error, ClientAuthenticationError):
            return False  # Don't retry auth errors

        return True  # Retry unknown errors
    
    def check_connection(self) -> bool:
        """
        Check if GitHub AI service is available
        
        Returns:
            True if connection is successful, False otherwise
        """
        if not self.client:
            return False
        
        try:
            # Test with a simple message
            test_messages = [
                SystemMessage(content="You are a helpful assistant."),
                UserMessage(content="Hello")
            ]
            
            response = self.client.complete(
                messages=test_messages,
                model=self.model,
                max_tokens=10,
                temperature=0.1
            )
            
            logger.info("✅ GitHub AI connection successful")
            return True
        except Exception as e:
            logger.error(f"❌ GitHub AI connection failed: {e}")
            return False
    
    def check_model_availability(self) -> bool:
        """
        Check if the specified model is available
        
        Returns:
            True if model is available, False otherwise
        """
        if not self.client:
            return False
        
        try:
            # Test model availability with a minimal request
            test_messages = [
                SystemMessage(content="Test"),
                UserMessage(content="Hi")
            ]
            
            response = self.client.complete(
                messages=test_messages,
                model=self.model,
                max_tokens=5,
                temperature=0.1
            )
            
            logger.info(f"✅ Model {self.model} is available")
            return True
        except Exception as e:
            logger.error(f"❌ Model {self.model} availability check failed: {e}")
            return False

    def generate_response(self, prompt: str, stream: bool = False) -> Optional[str]:
        """
        Generate response using GitHub AI with enhanced error handling and retry logic

        Args:
            prompt: Input prompt
            stream: Whether to stream the response (not used in sync method)

        Returns:
            Generated response or None if failed
        """
        if not self.client:
            logger.error("❌ GitHub AI client not initialized")
            return None



        for attempt in range(self.max_retries + 1):
            try:
                if attempt > 0:
                    delay = self._calculate_retry_delay(attempt - 1)
                    logger.info(f"🔄 Retry attempt {attempt}/{self.max_retries} after {delay:.2f}s delay")
                    time.sleep(delay)

                logger.info(f"🔄 Generating response with model: {self.model} (attempt {attempt + 1})")

                # Convert prompt to messages format
                messages = [
                    SystemMessage(content="You are a helpful Vietnamese product recommendation assistant for LapXpert."),
                    UserMessage(content=prompt)
                ]

                response = self.client.complete(
                    messages=messages,
                    model=self.model,
                    max_tokens=2500,  # Increased for complete Vietnamese responses
                    temperature=0.7,
                    top_p=0.9,
                    timeout=self.timeout  # 180-second timeout compatibility
                )

                if response and response.choices:
                    content = response.choices[0].message.content
                    if content:
                        content_length = len(content)
                        logger.info(f"✅ Response generated successfully (length: {content_length} characters)")

                        # Check if response might be truncated
                        if content_length > 2000:
                            logger.info("📏 Long response generated - checking for completeness")

                        return content.strip()
                    else:
                        logger.warning("⚠️ Empty response content received")
                        return None
                else:
                    logger.error("❌ No response content received")
                    continue

            except HttpResponseError as e:
                logger.error(f"❌ HTTP error generating response (attempt {attempt + 1}): {e}")

                if not self._is_retryable_error(e) or attempt == self.max_retries:
                    return None

            except Exception as e:
                logger.error(f"❌ Error generating response (attempt {attempt + 1}): {e}")

                if not self._is_retryable_error(e) or attempt == self.max_retries:
                    return None

        return None



    def generate_product_recommendation(self,
                                      user_query: str,
                                      similar_products: List[Dict[str, Any]]) -> Optional[str]:
        """
        Generate Vietnamese product recommendation

        Args:
            user_query: User's query in Vietnamese
            similar_products: List of similar products from vector search

        Returns:
            AI-generated product recommendation in Vietnamese
        """
        try:
            # Generate prompt using template
            prompt = self.prompt_templates.product_recommendation_prompt(
                user_query, similar_products
            )

            logger.info(f"🎯 Generating product recommendation for query: '{user_query}'")

            # Generate response
            response = self.generate_response(prompt)

            if response:
                logger.info("✅ Product recommendation generated successfully")
                return response
            else:
                logger.error("❌ Failed to generate product recommendation")
                return None

        except Exception as e:
            logger.error(f"❌ Error in product recommendation generation: {e}")
            return None

    def generate_general_chat_response(self, user_message: str) -> Optional[str]:
        """
        Generate general chat response

        Args:
            user_message: User's message

        Returns:
            AI-generated chat response in Vietnamese
        """
        try:
            # Generate prompt using template
            prompt = self.prompt_templates.general_chat_prompt(user_message)

            logger.info(f"💬 Generating chat response for message: '{user_message}'")

            # Generate response
            response = self.generate_response(prompt)

            if response:
                logger.info("✅ Chat response generated successfully")
                return response
            else:
                logger.error("❌ Failed to generate chat response")
                return None

        except Exception as e:
            logger.error(f"❌ Error in chat response generation: {e}")
            return None





    def get_fallback_response(self, user_query: str) -> str:
        """
        Get fallback response when GitHub AI is unavailable

        Args:
            user_query: User's query

        Returns:
            Fallback response in Vietnamese
        """
        fallback_responses = {
            "product": "Xin lỗi, hệ thống AI tư vấn hiện đang bảo trì. Vui lòng liên hệ nhân viên tư vấn của LapXpert để được hỗ trợ tốt nhất.",
            "general": "Cảm ơn bạn đã liên hệ với LapXpert! Hệ thống AI hiện đang bảo trì. Vui lòng thử lại sau hoặc liên hệ hotline để được hỗ trợ trực tiếp."
        }

        # Simple keyword detection for fallback type
        product_keywords = ["laptop", "điện thoại", "máy tính", "gaming", "sản phẩm", "mua", "giá"]

        if any(keyword in user_query.lower() for keyword in product_keywords):
            return fallback_responses["product"]
        else:
            return fallback_responses["general"]




def test_github_ai_service():
    """Test the GitHub AI service functionality with enhanced error handling"""
    logger.info("🧪 Testing GitHub AI Service with Enhanced Error Handling...")

    # Initialize service with enhanced configuration
    service = GitHubAIService(
        max_retries=2,  # Reduced for testing
        initial_retry_delay=0.5,
        max_retry_delay=5.0
    )

    # Display initial health status
    # health_status = service.get_health_status()
    # logger.info(f"📊 Initial health status: {health_status['status']}")

    # Test connection
    if not service.check_connection():
        logger.error("❌ GitHub AI service connection failed")
        return False

    # Test model availability
    if not service.check_model_availability():
        logger.warning("⚠️ Model not available, but connection works")

    # Test product recommendation with error handling
    sample_products = [
        {
            "ten_san_pham": "Laptop ASUS ROG Strix G15",
            "gia_ban": 25000000,
            "mo_ta": "Gaming laptop với CPU AMD Ryzen 7, RTX 3060, RAM 16GB"
        },
        {
            "ten_san_pham": "Laptop MSI Gaming GF63",
            "gia_ban": 18000000,
            "mo_ta": "Gaming laptop với CPU Intel i5, GTX 1650, RAM 8GB"
        }
    ]

    user_query = "Tôi cần laptop gaming trong tầm giá 20 triệu"

    logger.info(f"🔍 Testing product recommendation with retry logic for: '{user_query}'")
    recommendation = service.generate_product_recommendation(user_query, sample_products)

    if recommendation:
        logger.info("✅ Product recommendation test successful")
        logger.info(f"📝 Response preview: {recommendation[:200]}...")
    else:
        logger.error("❌ Product recommendation test failed")
        logger.info(f"🔄 Fallback response: {service.get_fallback_response(user_query)}")

    # Test general chat with metrics
    chat_message = "Xin chào, LapXpert có những sản phẩm gì mới không?"

    logger.info(f"💬 Testing general chat with metrics for: '{chat_message}'")
    chat_response = service.generate_general_chat_response(chat_message)

    if chat_response:
        logger.info("✅ General chat test successful")
        logger.info(f"📝 Response preview: {chat_response[:200]}...")
    else:
        logger.error("❌ General chat test failed")
        logger.info(f"🔄 Fallback response: {service.get_fallback_response(chat_message)}")

    # # Display final metrics
    # metrics = service.get_service_metrics()
    # logger.info(f"📊 Final service metrics:")
    # logger.info(f"   - Total requests: {metrics['total_requests']}")
    # logger.info(f"   - Success rate: {metrics['success_rate_percent']}%")
    # logger.info(f"   - Average response time: {metrics['average_response_time_seconds']}s")
    # logger.info(f"   - Circuit breaker status: {'Open' if metrics['circuit_breaker']['is_open'] else 'Closed'}")
    #
    # # Test health status
    # final_health = service.get_health_status()
    # logger.info(f"🏥 Final health status: {final_health['status']}")

    logger.info("🎉 Enhanced GitHub AI service testing completed!")
    return True

if __name__ == "__main__":
    test_github_ai_service()
