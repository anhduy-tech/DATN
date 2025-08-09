"""
Vietnamese Embedding Service for LapXpert AI Chat
Provides enhanced Vietnamese text processing with pyvi tokenization
and superior dangvantuan/vietnamese-embedding model (88.33% vs 84.65% Pearson correlation)
"""

import logging
from sentence_transformers import SentenceTransformer
from pyvi.ViTokenizer import tokenize
import numpy as np
from typing import List, Union

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class VietnameseEmbeddingService:
    """
    Enhanced Vietnamese embedding service for AI chat functionality
    """
    
    def __init__(self, model_name: str = "dangvantuan/vietnamese-embedding"):
        """
        Initialize the Vietnamese embedding service
        
        Args:
            model_name: The Vietnamese embedding model to use
        """
        self.model_name = model_name
        self.model = None
        self._load_model()
    
    def _load_model(self):
        """Load the Vietnamese embedding model"""
        try:
            logger.info(f"🔄 Loading Vietnamese embedding model: {self.model_name}")
            # Force CPU usage to avoid CUDA memory issues
            self.model = SentenceTransformer(self.model_name, device='cpu')
            logger.info(f"✅ Model loaded successfully: {self.model_name} (using CPU)")
            logger.info(f"📏 Embedding dimension: {self.model.get_sentence_embedding_dimension()}")
        except Exception as e:
            logger.error(f"❌ Failed to load model: {e}")
            raise
    
    def preprocess_vietnamese_text(self, text: str) -> str:
        """
        Preprocess Vietnamese text using pyvi tokenization
        
        Args:
            text: Raw Vietnamese text
            
        Returns:
            Tokenized Vietnamese text
        """
        if not text or not isinstance(text, str):
            return ""
        
        try:
            # Use pyvi to tokenize Vietnamese text
            tokenized_text = tokenize(text.strip())
            logger.debug(f"Tokenized: '{text[:50]}...' -> '{tokenized_text[:50]}...'")
            return tokenized_text
        except Exception as e:
            logger.warning(f"Error tokenizing text: {e}. Using original text.")
            return text.strip()
    
    def encode_text(self, text: Union[str, List[str]]) -> np.ndarray:
        """
        Generate embeddings for Vietnamese text with preprocessing
        
        Args:
            text: Single text string or list of text strings
            
        Returns:
            Numpy array of embeddings
        """
        if isinstance(text, str):
            # Single text
            preprocessed = self.preprocess_vietnamese_text(text)
            return self.model.encode(preprocessed)
        elif isinstance(text, list):
            # Multiple texts
            preprocessed_texts = [self.preprocess_vietnamese_text(t) for t in text]
            return self.model.encode(preprocessed_texts)
        else:
            raise ValueError("Text must be string or list of strings")
    
    def compute_similarity(self, text1: str, text2: str) -> float:
        """
        Compute cosine similarity between two Vietnamese texts
        
        Args:
            text1: First Vietnamese text
            text2: Second Vietnamese text
            
        Returns:
            Cosine similarity score (0-1)
        """
        embeddings = self.encode_text([text1, text2])
        
        # Compute cosine similarity
        dot_product = np.dot(embeddings[0], embeddings[1])
        norm1 = np.linalg.norm(embeddings[0])
        norm2 = np.linalg.norm(embeddings[1])
        
        similarity = dot_product / (norm1 * norm2)
        return float(similarity)
    
    def find_most_similar(self, query: str, candidates: List[str], top_k: int = 5) -> List[tuple]:
        """
        Find most similar texts to a query from a list of candidates
        
        Args:
            query: Query text in Vietnamese
            candidates: List of candidate texts
            top_k: Number of top results to return
            
        Returns:
            List of (text, similarity_score) tuples sorted by similarity
        """
        if not candidates:
            return []
        
        # Generate embeddings
        query_embedding = self.encode_text(query)
        candidate_embeddings = self.encode_text(candidates)
        
        # Compute similarities
        similarities = []
        for i, candidate in enumerate(candidates):
            similarity = np.dot(query_embedding, candidate_embeddings[i]) / (
                np.linalg.norm(query_embedding) * np.linalg.norm(candidate_embeddings[i])
            )
            similarities.append((candidate, float(similarity)))
        
        # Sort by similarity and return top_k
        similarities.sort(key=lambda x: x[1], reverse=True)
        return similarities[:top_k]

def test_vietnamese_embedding_service():
    """Test the Vietnamese embedding service"""
    logger.info("🧪 Testing Vietnamese Embedding Service...")
    
    # Initialize service
    service = VietnameseEmbeddingService()
    
    # Test texts
    query = "laptop gaming mạnh mẽ"
    candidates = [
        "Laptop gaming ASUS ROG với CPU Intel Core i7 và GPU RTX 4070",
        "Điện thoại iPhone 15 Pro Max màu xanh dương",
        "Máy tính xách tay Dell XPS 13 cho văn phòng",
        "Gaming laptop MSI với card đồ họa RTX 4080",
        "Tai nghe không dây AirPods Pro thế hệ 2"
    ]
    
    # Test similarity search
    logger.info(f"🔍 Query: '{query}'")
    results = service.find_most_similar(query, candidates, top_k=3)
    
    logger.info("📊 Top 3 most similar results:")
    for i, (text, score) in enumerate(results, 1):
        logger.info(f"  {i}. Score: {score:.4f} - '{text}'")
    
    # Test individual similarity
    text1 = "laptop gaming"
    text2 = "máy tính chơi game"
    similarity = service.compute_similarity(text1, text2)
    logger.info(f"🔗 Similarity between '{text1}' and '{text2}': {similarity:.4f}")
    
    logger.info("✅ Vietnamese Embedding Service test completed!")

if __name__ == "__main__":
    test_vietnamese_embedding_service()
