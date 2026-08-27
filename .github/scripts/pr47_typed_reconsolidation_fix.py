from pathlib import Path

path = Path('src/main/java/nl/hauntedmc/ailex/assistant/infrastructure/memory/AssistantMemoryService.java')
text = path.read_text()
old = '''        Set<String> ids = evidenceIds.stream()
                .filter(id -> id != null && id.startsWith("memory."))
                .map(id -> id.substring("memory.".length()))
                .collect(java.util.stream.Collectors.toSet());
'''
new = '''        Set<String> ids = evidenceIds.stream()
                .filter(id -> id != null && id.startsWith("memory."))
                .map(MemoryEvidenceId::recordId)
                .filter(id -> !id.isBlank())
                .collect(java.util.stream.Collectors.toSet());
'''
if text.count(old) != 1:
    raise SystemExit(f'AssistantMemoryService reconsolidation match count={text.count(old)}')
path.write_text(text.replace(old, new, 1))

path = Path('src/main/java/nl/hauntedmc/ailex/assistant/infrastructure/memory/AssistantRelationshipMemoryService.java')
text = path.read_text()
old = '        records.forEach(record -> evidence.add("memory." + record.id()));\n'
new = '        records.forEach(record -> evidence.add(MemoryEvidenceId.forRecord(record)));\n'
if text.count(old) != 1:
    raise SystemExit(f'AssistantRelationshipMemoryService evidence match count={text.count(old)}')
path.write_text(text.replace(old, new, 1))
