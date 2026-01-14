import requests
import json
import sys

def test_unzip_save_doc():
    # Configuration
    base_url = "http://localhost:8080"
    endpoint = "/unzip_save_doc"
    
    # Test Parameters
    # Ensure this document_link_id exists in your Documentum mock or service
    document_link_id = "000000000a" 
    client_id = "CLIENT_TEST_001"
    
    url = f"{base_url}{endpoint}"
    params = {
        "document_link_id": document_link_id,
        "client_id": client_id
    }
    
    print(f"🚀 Starting Test Request...")
    print(f"URL: {url}")
    print(f"Parameters: {params}")
    print("-" * 50)
    
    try:
        # Make the GET request
        response = requests.get(url, params=params)
        
        # Check Status Code
        print(f"Response Status Code: {response.status_code}")
        
        if response.status_code == 200:
            try:
                data = response.json()
                print("\n✅ Response JSON received:")
                print(json.dumps(data, indent=2))
                
                # Validation Logic
                doc_ids = data.get("docIds")
                metadata = data.get("metadata")
                
                validations = []
                
                # Check 1: structure matches
                if doc_ids is not None and isinstance(doc_ids, list):
                    validations.append("✅ 'docIds' field is a list")
                else:
                    validations.append("❌ 'docIds' field is missing or not a list")
                    
                if metadata is not None and isinstance(metadata, dict):
                    validations.append("✅ 'metadata' field is present")
                    if metadata.get("name") and metadata.get("children") is not None:
                         validations.append("✅ 'metadata' has expected fields (name, children)")
                else:
                    validations.append("❌ 'metadata' field is missing")

                # Print Validation results
                print("\nValidations:")
                for v in validations:
                    print(v)
                
                if all("✅" in v for v in validations):
                     print("\n🎉 TEST PASSED: Endpoint is functioning correctly.")
                else:
                     print("\n⚠️ TEST FAILED: Response structure validation failed.")
                     
            except json.JSONDecodeError:
                print("❌ Failed to decode JSON response.")
                print(f"Response text: {response.text}")
        else:
            print(f"❌ Request Failed with status {response.status_code}")
            print(f"Response text: {response.text}")

    except requests.exceptions.ConnectionError:
        print(f"❌ ConnectError: Could not connect to {base_url}. \nIs the 'unzipper-service' running and mapped to port 8080?")
    except Exception as e:
        print(f"❌ An error occurred: {e}")

if __name__ == "__main__":
    # Check for requests module
    try:
        import requests
    except ImportError:
        print("This script requires the 'requests' library.")
        print("Please install it running: pip install requests")
        sys.exit(1)
        
    test_unzip_save_doc()
