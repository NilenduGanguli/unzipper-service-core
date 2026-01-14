import concurrent.futures
import time
import os
import statistics
import random
import string
import zipfile
import io
import uuid
import urllib.request
import urllib.error

# Configuration
SERVER_URL = "http://localhost:8080/unzip"
TEST_FOLDER = "load_test_data"

# Helper Functions (reused from benchmark)
def random_string(length):
    return ''.join(random.choices(string.ascii_letters + string.digits, k=length))

def add_files_to_zip(zf, count, size_per_file, prefix=""):
    for i in range(count):
        filename = f"{prefix}file_{i}_{random_string(5)}.txt"
        data = (random_string(100) * (size_per_file // 100)).encode('utf-8')
        zf.writestr(filename, data)

def create_nested_zip(filename, target_size_mb, depth=1):
    path = os.path.join(TEST_FOLDER, filename)
    total_bytes = target_size_mb * 1024 * 1024
    
    # Simplified creation logic for load testing
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as zf:
        # Base files
        add_files_to_zip(zf, 5, total_bytes // 10) # 10% size in regular files
        
        # Nested zip
        if depth > 0:
            nested_io = io.BytesIO()
            with zipfile.ZipFile(nested_io, 'w', zipfile.ZIP_DEFLATED) as upload_zf:
                add_files_to_zip(upload_zf, 10, total_bytes // 2) # 50% size nested
            zf.writestr(f"nested.zip", nested_io.getvalue())
            
    return path

def upload_file(url, file_path):
    boundary = uuid.uuid4().hex
    filename = os.path.basename(file_path)
    
    with open(file_path, 'rb') as f:
        file_content = f.read()

    data = []
    data.append(f'--{boundary}')
    data.append(f'Content-Disposition: form-data; name="file"; filename="{filename}"')
    data.append('Content-Type: application/zip')
    data.append('')
    
    body_start = '\r\n'.join(data).encode('utf-8') + b'\r\n'
    body_end = f'\r\n--{boundary}--\r\n'.encode('utf-8')
    full_body = body_start + file_content + body_end
    
    req = urllib.request.Request(url, data=full_body)
    req.add_header('Content-Type', f'multipart/form-data; boundary={boundary}')
    req.add_header('Content-Length', len(full_body))
    
    start_time = time.time()
    try:
        with urllib.request.urlopen(req) as response:
            response.read() # Consume response
            return time.time() - start_time, response.status
    except urllib.error.HTTPError as e:
        return time.time() - start_time, e.code
    except Exception as e:
        return time.time() - start_time, 999 # Connection error

def generate_test_data():
    if not os.path.exists(TEST_FOLDER):
        os.makedirs(TEST_FOLDER)
    
    pool = []
    print("Generating test data pool...")
    pool.append({"path": create_nested_zip("small_flat.zip", 1, 0), "type": "Small Flat"})
    pool.append({"path": create_nested_zip("medium_nested.zip", 5, 1), "type": "Medium Nested"})
    pool.append({"path": create_nested_zip("large_mixed.zip", 15, 2), "type": "Large Mixed"})
    return pool

def run_wave(wave_name, num_requests, concurrency, file_pool):
    print(f"\n>>> Running Wave: {wave_name}")
    print(f"    Configuration: {num_requests} requests, {concurrency} concurrent workers")
    
    latencies = []
    status_codes = {}
    start_time = time.time()
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = []
        for _ in range(num_requests):
            file_info = random.choice(file_pool)
            futures.append(executor.submit(upload_file, SERVER_URL, file_info['path']))
            
        for future in concurrent.futures.as_completed(futures):
            latency, status = future.result()
            latencies.append(latency)
            status_codes[status] = status_codes.get(status, 0) + 1

    total_duration = time.time() - start_time
    
    # Analysis
    avg_latency = statistics.mean(latencies) if latencies else 0
    p95_latency = sorted(latencies)[int(len(latencies) * 0.95)] if latencies else 0
    throughput = num_requests / total_duration
    
    print(f"    Duration: {total_duration:.2f}s")
    print(f"    Throughput: {throughput:.2f} req/s")
    print(f"    Avg Latency: {avg_latency:.4f}s")
    print(f"    P95 Latency: {p95_latency:.4f}s")
    print(f"    Status Codes: {status_codes}")
    return throughput

def main():
    try:
        data_pool = generate_test_data()
        
        # Test 1: Warmup
        run_wave("Warmup", 10, 2, data_pool)
        
        # Test 2: Concurrent Small/Medium Load
        run_wave("Sustainable Load", 50, 10, data_pool)
        
        # Test 3: High Concurrency Spike
        run_wave("Spike Test", 100, 30, data_pool)
        
        # Test 4: Maximum Parallelism Stress (Saturation)
        # Note: This might cause timeouts or errors depending on Docker resources
        run_wave("Stress Test", 50, 50, data_pool)

    finally:
        # Cleanup
        if os.path.exists(TEST_FOLDER):
            import shutil
            shutil.rmtree(TEST_FOLDER)
            print("\nTest data cleaned up.")

if __name__ == "__main__":
    main()
