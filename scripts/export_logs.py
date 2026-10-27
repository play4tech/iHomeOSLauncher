import json
import os

def export_logs():
    payload_path = "test_results/test_execution_payload.json"
    logs_dir = "test_results/logs"
    os.makedirs(logs_dir, exist_ok=True)
    
    with open(payload_path, "r", encoding="utf-8") as f:
        data = json.load(f)
        
    for item in data:
        test_id = item["id"]
        log_content = item.get("log_proof", "")
        header = (
            f"Test Case: {test_id} - {item.get('name')}\n"
            f"Category: {item.get('category')}\n"
            f"Status: {item.get('status')}\n"
            f"Duration: {item.get('duration_sec')}s\n"
            f"{'='*60}\n\n"
        )
        file_path = os.path.join(logs_dir, f"{test_id}.log")
        with open(file_path, "w", encoding="utf-8") as out:
            out.write(header + log_content)
            
    print(f"Successfully exported {len(data)} test logs to {logs_dir}/")

if __name__ == "__main__":
    export_logs()
