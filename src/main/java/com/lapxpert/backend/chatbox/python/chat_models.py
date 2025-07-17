"""
Chat Models for LapXpert AI Chat
Pydantic models for chat requests and responses, including conversational AI models
"""

from pydantic import BaseModel
from typing import List, Optional

class SearchRequest(BaseModel):
    query: str
    top_k: int = 5

class ChatRequest(BaseModel):
    message: str
    user_id: Optional[str] = None
    session_id: Optional[str] = None
    top_k: int = 5

class ProductRecommendation(BaseModel):
    san_pham_chi_tiet_id: int
    ten_san_pham: str
    gia_ban: float
    mo_ta: str
    similarity_score: float

class ChatResponse(BaseModel):
    ai_response: str
    product_recommendations: List[ProductRecommendation]
    query_processed: str
    ai_available: bool  # Updated from ollama_available to reflect GitHub AI usage

# Conversational AI Models (merged from conversational_models.py)
class ConversationalRequest(BaseModel):
    message: str
    user_id: Optional[str] = None
    session_id: Optional[str] = None

class ConversationalResponse(BaseModel):
    ai_response: str
    query_processed: str
    ai_available: bool  # Updated from ollama_available to reflect GitHub AI usage
