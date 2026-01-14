import os
import time
import zipfile
import io
import random
import string
import urllib.request
import urllib.parse
import json
import uuid

# Configuration
SERVER_URL = "http://localhost:8080/unzip"
TEST_FOLDER = "benchmark_data"

def random_string(length):
    return ''.join(random.choices(string.ascii_letters + string.digits, k=length))

def create_dummy_data(size):
    return os.urandom(size)

def add_files_to_zip(zf, count, size_per_file, prefix=""):
    for i in range(count):
        filename = f"{prefix}file_{i}_{random_string(5)}.txt"
        # Create some compressible text data mixed with random data
        data = (random_string(100) * (size_per_file // 100)).encode('utf-8')
        zf.writestr(filename, data)

def create_nested_zip(filename, target_size_mb, depth=1, files_per_level=5):
    """
    Creates a zip file with target total uncompressed size approx target_size_mb.
    Structure helps test the recursive capabilities.
    """
    path = os.path.join(TEST_FOLDER, filename)
    
    # Calculate approx size per file
    # simple heuristic: distribute size across depth
    # This is rough estimation
    total_bytes = target_size_mb * 1024 * 1024
    
    # We will just generate a zip. If depth > 0, we treat one of the files as a zip
    
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as zf:
        current_depth_size = total_bytes // (depth + 1)
        file_size = current_depth_size // files_per_level
        
        # Add standard files
        add_files_to_zip(zf, files_per_level, file_size)
        
        # Add nested zip if needed
        if depth > 0:
            nested_io = io.BytesIO()
            with zipfile.ZipFile(nested_io, 'w', zipfile.ZIP_DEFLATED) as upload_zf:
                # Recursive fill
                inner_size = total_bytes - current_depth_size
                inner_file_size = inner_size // files_per_level
                add_files_to_zip(upload_zf, files_per_level, inner_file_size, prefix=f"level_{depth}_")
                
                # We could go deeper but for simplicity strictly nesting logic is simpler iteratively or just 1 level here for demo
                # Let's do true recursion for "nested.zip" inside
                if depth > 1:
                     # This simplified generator focuses on 1-level deep or just varying sizes for the benchmark
                     pass 

            zf.writestr(f"nested_level_{depth}.zip", nested_io.getvalue())
            
    return path, os.path.getsize(path)

def upload_file(url, file_path):
    """
    Uploads a file using multipart/form-data using standard library
    """
    boundary = uuid.uuid4().hex
    filename = os.path.basename(file_path)
    
    data = []
    data.append(f'--{boundary}')
    data.append(f'Content-Disposition: form-data; name="file"; filename="{filename}"')
    data.append('Content-Type: application/zip')
    data.append('')
    
    with open(file_path, 'rb') as f:
        file_content = f.read()
        
    body_start = '\r\n'.join(data).encode('utf-8') + b'\r\n'
    body_end = f'\r\n--{boundary}--\r\n'.encode('utf-8')
    
    full_body = body_start + file_content + body_end
    
    req = urllib.request.Request(url, data=full_body)
    req.add_header('Content-Type', f'multipart/form-data; boundary={boundary}')
    req.add_header('Content-Length', len(full_body))
    
    start_time = time.time()
    try:
        with urllib.request.urlopen(req) as response:
            resp_body = response.read()
            resp_decoded = resp_body.decode('utf-8')
            print(f"Response: {json.dumps(json.loads(resp_decoded), indent=2)}")
            end_time = time.time()
            return end_time - start_time, response.status, len(resp_body)
    except urllib.error.HTTPError as e:
        return time.time() - start_time, e.code, 0
    except Exception as e:
        print(f"Error: {e}")
        return 0, 500, 0

def run_benchmarks():
    if not os.path.exists(TEST_FOLDER):
        os.makedirs(TEST_FOLDER)
        
    print(f"{'Test Case':<30} | {'Size (MB)':<10} | {'Latency (s)':<12} | {'Status':<8}")
    print("-" * 70)
    
    test_cases = [
        ("Small Flat", 1, 0),
        ("Medium Flat", 10, 0),
        ("Medium Nested", 10, 1),
        ("Large Flat", 50, 0),
        ("Large Nested", 50, 1)
    ]
    
    for name, size_mb, depth in test_cases:
        filename = f"{name.lower().replace(' ', '_')}.zip"
        filepath, actual_size = create_nested_zip(filename, size_mb, depth)
        
        # Run test
        latency, status, resp_len = upload_file(SERVER_URL, filepath)
        
        print(f"{name:<30} | {size_mb:<10} | {latency:.4f}       | {status:<8}")
        
        # Clean up
        os.remove(filepath)

    os.rmdir(TEST_FOLDER)

if __name__ == "__main__":
    print("Starting Benchmark...")
    print("Generating zip files and testing endpoint...")
    run_benchmarks()
