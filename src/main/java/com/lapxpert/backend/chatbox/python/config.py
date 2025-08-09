"""
LapXpert AI Chat Configuration
Centralized configuration constants for Vietnamese AI chat functionality

This module contains all shared configuration constants to eliminate duplication
across main.py and scripts/update_embeddings.py
"""

from typing import Dict, Any

# ================== Database Configuration ==================
DB_CONFIG: Dict[str, Any] = {
    'dbname': 'lapxpert8',
    'user': 'lapxpert',
    'password': 'lapxpert!',
    'host': 'lapxpert-db.khoalda.dev',
    'port': 5432,
}

# ================== Database Table Configuration ==================
# Main table for storing product embeddings
TABLE_NAME: str = "san_pham_embeddings"

# View containing source data for embedding generation
VIEW_NAME: str = "san_pham_embed_view"

# ================== AI Model Configuration ==================
# Vietnamese embedding model - superior performance (88.33% vs 84.65% Pearson correlation)
MODEL_NAME: str = "dangvantuan/vietnamese-embedding"

# Embedding vector dimensions (compatible with dangvantuan/vietnamese-embedding)
EMBEDDING_DIM: int = 768

# ================== Search Configuration ==================
# Default number of top results to return in similarity searches
TOP_K: int = 5

# ================== GitHub AI Configuration ==================
# GitHub AI endpoint for Mistral Medium 3 (25.05) model
GITHUB_AI_ENDPOINT: str = "https://models.github.ai/inference"

# Mistral Medium 3 model identifier
GITHUB_AI_MODEL: str = "mistral-ai/mistral-medium-2505"

# Default timeout for AI requests (seconds)
AI_REQUEST_TIMEOUT: int = 180

# ================== Application Configuration ==================
# FastAPI application settings
APP_HOST: str = "localhost"
APP_PORT: int = 8001

# Logging configuration
LOG_LEVEL: str = "INFO"
LOG_FORMAT: str = "%(asctime)s - %(levelname)s - %(message)s"
