import json

with open("test_results/test_execution_payload.json", "r", encoding="utf-8") as f:
    data = json.load(f)

print(f"Total Tests: {len(data)}")
counts = {}
for d in data:
    status = d.get("status")
    counts[status] = counts.get(status, 0) + 1
print(f"Summary: {counts}")

print("\n=== NON-PASSING TESTS ===")
for d in data:
    status = d.get("status")
    if status != "PASS":
        tc_id = d.get("id")
        name = d.get("name")
        reason = d.get("status_reason", "")
        err = d.get("error", "")
        print(f"[{tc_id}] {status}: {name}")
        if reason:
            print(f"   Reason: {reason}")
        if err:
            print(f"   Error: {err}")
