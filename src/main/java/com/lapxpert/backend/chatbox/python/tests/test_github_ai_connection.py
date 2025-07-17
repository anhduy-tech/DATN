"""
Test GitHub AI connection and azure-ai-inference SDK functionality
"""

import os
import logging
import asyncio
from typing import Optional

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def test_azure_ai_inference_import():
    """Test that azure-ai-inference SDK can be imported"""
    try:
        from azure.ai.inference import ChatCompletionsClient
        from azure.ai.inference.aio import ChatCompletionsClient as AsyncChatCompletionsClient
        from azure.ai.inference.models import SystemMessage, UserMessage
        from azure.core.credentials import AzureKeyCredential
        from azure.core.exceptions import HttpResponseError
        
        logger.info("✅ azure-ai-inference SDK imported successfully")
        logger.info(f"📦 Available classes: ChatCompletionsClient, AsyncChatCompletionsClient, SystemMessage, UserMessage")
        return True
        
    except ImportError as e:
        logger.error(f"❌ Failed to import azure-ai-inference SDK: {e}")
        return False

def test_github_token_configuration():
    """Test GitHub token configuration"""
    github_token = os.getenv('GITHUB_TOKEN')

    if not github_token:
        logger.warning("⚠️ GITHUB_TOKEN environment variable not set")
        logger.info("💡 To set up GitHub token:")
        logger.info("   1. Create a GitHub personal access token with 'models:read' permission")
        logger.info("   2. Set environment variable: export GITHUB_TOKEN=your_token_here")
        logger.info("   3. Or configure in application.properties: ai.chat.github.token=your_token_here")
        return False
    
    # Basic token validation (should start with 'ghp_' for personal access tokens)
    if github_token.startswith('ghp_') and len(github_token) >= 40:
        logger.info("✅ GITHUB_TOKEN appears to be properly formatted")
        logger.info(f"🔑 Token prefix: {github_token[:7]}...")
        return True
    else:
        logger.warning("⚠️ GITHUB_TOKEN may not be properly formatted")
        logger.info(f"🔑 Token prefix: {github_token[:7]}...")
        return False

def test_github_ai_basic_connection():
    """Test basic connection to GitHub AI endpoint"""
    github_token = os.getenv('GITHUB_TOKEN')
    
    if not github_token:
        logger.error("❌ Cannot test connection without GITHUB_TOKEN")
        return False
    
    try:
        from azure.ai.inference import ChatCompletionsClient
        from azure.ai.inference.models import UserMessage
        from azure.core.credentials import AzureKeyCredential
        from azure.core.exceptions import HttpResponseError
        
        # Initialize client
        endpoint = "https://models.github.ai/inference"
        model = "mistral-ai/mistral-medium-2505"
        
        client = ChatCompletionsClient(
            endpoint=endpoint,
            credential=AzureKeyCredential(github_token)
        )
        
        logger.info(f"🔄 Testing connection to {endpoint}")
        logger.info(f"🤖 Using model: {model}")
        
        # Test with a simple Vietnamese message
        messages = [
            UserMessage(content="Xin chào! Bạn có thể trả lời bằng tiếng Việt không?")
        ]
        
        # Set a short timeout for testing
        response = client.complete(
            messages=messages,
            model=model,
            max_tokens=50,
            read_timeout=30
        )
        
        if response and response.choices:
            response_content = response.choices[0].message.content
            logger.info("✅ GitHub AI connection successful!")
            logger.info(f"📝 Response preview: {response_content[:100]}...")
            return True
        else:
            logger.error("❌ GitHub AI returned empty response")
            return False
            
    except HttpResponseError as e:
        logger.error(f"❌ GitHub AI HTTP error: {e.status_code} - {e.reason}")
        if e.status_code == 401:
            logger.error("🔑 Authentication failed - check your GITHUB_TOKEN")
        elif e.status_code == 403:
            logger.error("🚫 Access forbidden - check token permissions")
        elif e.status_code == 429:
            logger.error("⏱️ Rate limit exceeded - try again later")
        return False
        
    except Exception as e:
        logger.error(f"❌ GitHub AI connection failed: {e}")
        return False

async def test_github_ai_async_connection():
    """Test async connection to GitHub AI endpoint"""
    github_token = os.getenv('GITHUB_TOKEN')
    
    if not github_token:
        logger.error("❌ Cannot test async connection without GITHUB_TOKEN")
        return False
    
    try:
        from azure.ai.inference.aio import ChatCompletionsClient
        from azure.ai.inference.models import UserMessage
        from azure.core.credentials import AzureKeyCredential
        from azure.core.exceptions import HttpResponseError
        
        # Initialize async client
        endpoint = "https://models.github.ai/inference"
        model = "mistral-ai/mistral-medium-2505"
        
        client = ChatCompletionsClient(
            endpoint=endpoint,
            credential=AzureKeyCredential(github_token)
        )
        
        logger.info(f"🔄 Testing async connection to {endpoint}")
        
        # Test with a simple Vietnamese message
        messages = [
            UserMessage(content="Chào bạn! Hãy trả lời ngắn gọn bằng tiếng Việt.")
        ]
        
        # Test async call
        response = await client.complete(
            messages=messages,
            model=model,
            max_tokens=30,
            read_timeout=30
        )
        
        if response and response.choices:
            response_content = response.choices[0].message.content
            logger.info("✅ GitHub AI async connection successful!")
            logger.info(f"📝 Async response preview: {response_content[:100]}...")
            return True
        else:
            logger.error("❌ GitHub AI async returned empty response")
            return False
            
    except HttpResponseError as e:
        logger.error(f"❌ GitHub AI async HTTP error: {e.status_code} - {e.reason}")
        return False
        
    except Exception as e:
        logger.error(f"❌ GitHub AI async connection failed: {e}")
        return False

def run_all_tests():
    """Run all GitHub AI connection tests"""
    logger.info("🧪 Starting GitHub AI Connection Tests...")
    logger.info("=" * 50)
    
    results = {}
    
    # Test 1: SDK Import
    logger.info("\n📦 Test 1: Azure AI Inference SDK Import")
    results['sdk_import'] = test_azure_ai_inference_import()
    
    # Test 2: GitHub Token Configuration
    logger.info("\n🔑 Test 2: GitHub Token Configuration")
    results['token_config'] = test_github_token_configuration()
    
    # Test 3: Basic Connection (only if token is available)
    if results['token_config']:
        logger.info("\n🌐 Test 3: GitHub AI Basic Connection")
        results['basic_connection'] = test_github_ai_basic_connection()
        
        # Test 4: Async Connection
        logger.info("\n⚡ Test 4: GitHub AI Async Connection")
        results['async_connection'] = asyncio.run(test_github_ai_async_connection())
    else:
        logger.info("\n⏭️ Skipping connection tests (no valid token)")
        results['basic_connection'] = False
        results['async_connection'] = False
    
    # Summary
    logger.info("\n" + "=" * 50)
    logger.info("📊 Test Results Summary:")
    
    passed = sum(results.values())
    total = len(results)
    
    for test_name, result in results.items():
        status = "✅ PASS" if result else "❌ FAIL"
        logger.info(f"  {test_name}: {status}")
    
    logger.info(f"\n🎯 Overall: {passed}/{total} tests passed")
    
    if passed == total:
        logger.info("🎉 All tests passed! GitHub AI setup is ready.")
    else:
        logger.info("⚠️ Some tests failed. Check the logs above for details.")
    
    return passed == total

if __name__ == "__main__":
    success = run_all_tests()
    exit(0 if success else 1)
