import json
with open('comments_new.json', encoding='utf-16') as f:
    data = json.load(f)
with open('output_new.txt', 'w', encoding='utf-8') as out:
    for c in data[-10:]:
        out.write(f"File: {c.get('path')} @ line {c.get('line', c.get('original_line'))}\n")
        out.write(f"User: {c['user']['login']}\n")
        out.write(f"Body: {c.get('body')}\n")
        out.write('-'*40 + '\n')
