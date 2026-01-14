import zipfile
import os
import io

def create_zip_with_content(filename, content):
    with zipfile.ZipFile(filename, 'w') as zf:
        for file_name, file_content in content.items():
            zf.writestr(file_name, file_content)

# Create dummy files
content1 = {
    'file1.txt': b'This is file 1',
    'file2.txt': b'This is file 2',
    'folder1/file3.txt': b'This is file 3 in a folder'
}

content2 = {
    'nested_file1.txt': b'Nested file 1 content',
    'nested_file2.txt': b'Nested file 2 content'
}

# Create a nested zip in memory
nested_zip_io = io.BytesIO()
with zipfile.ZipFile(nested_zip_io, 'w') as zf:
    for k, v in content2.items():
        zf.writestr(k, v)
nested_zip_bytes = nested_zip_io.getvalue()

# Add nested zip to content1
content1['nested.zip'] = nested_zip_bytes

# Create final zip
create_zip_with_content('test_payload.zip', content1)
print("Created test_payload.zip with nested zip inside.")
