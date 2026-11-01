#!/usr/bin/env python3
"""Offline documentary integrity checks; never product/runtime acceptance."""
from pathlib import Path
import hashlib,json
R=Path(__file__).resolve().parents[1]
load=lambda p:json.loads((R/p).read_text())
manifest=load('reference/manifest.json'); lookup={}
for item in manifest:
    raw=(R/item['reference_path']).read_bytes()
    assert hashlib.sha256(raw).hexdigest()==item['sha256'],item['reference_path']
    assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==item['source_blob']
    lookup[(item['source_commit'],item['source_path'])]=raw.decode().splitlines()
preserved={(m['source_commit'],m['source_path']):m['source_blob'] for m in manifest}
obligations=load('reference/preservation-obligations.json')
assert len(obligations)==380
for item in obligations:
    assert preserved[(item['source_commit'],item['source_path'])]==item['source_blob']
rows=load('docs/requirements.json')['requirements']
expected={i for g in load('reference/expected-requirement-ids.json').values() for i in g['ids']}
assert len(rows)==len(expected)==362 and {r['id'] for r in rows}==expected
ids={f'APK-P{(n-1)//4}-T{n:02}' for n in range(1,33)}
assert {r['primary_task'] for r in rows}==ids
for row in rows:
    src=row['ledger_source'];line=lookup[(src['commit'],src['path'])][src['line']-1]
    assert line==row['ledger_row_verbatim']
    assert hashlib.sha256(line.encode()).hexdigest()==src['line_sha256']
    cells=[cell.strip() for cell in line.strip('|').split('|')]
    rid=row['id']
    if rid.startswith('BRG-'):
        expected_obligation=line.split('** ',1)[1]
        expected_acceptance=expected_obligation
    elif rid.startswith('P'):
        expected_obligation,expected_acceptance=cells[2],cells[3]
    elif rid.startswith(('RM-','DM-','SUP-')):
        expected_obligation,expected_acceptance=cells[2],cells[4]
    else:
        expected_obligation,expected_acceptance=cells[1],cells[2]
    assert row['obligation_verbatim']==expected_obligation
    assert row['acceptance_verbatim']==expected_acceptance
    assert row['status']=='NOT_IMPLEMENTED'
    assert row['evidence']==dict.fromkeys(('HOST','COMPILE','ANDROID','PROVIDER','PHONE'),'NOT_RUN')
    for source in row['normative_sources']:
        for span in source['ranges']:
            if 'text_verbatim' in span:
                lines=lookup[(source['commit'],source['path'])]
                assert '\n'.join(lines[span['start']-1:span['end']])==span['text_verbatim']
task_doc=load('docs/tasks.json')
assert task_doc['phases']==8 and task_doc['tasks_total']==32 and task_doc['completed_count']==0
assert task_doc['phone_gates']=={'1':[9,10,11,12],'2':[17,18,19,20],'3':[21,22,23,24],'4':[29,30,31,32]}
status=load('docs/status.json')
assert status['phase_total']==8 and status['task_total']==32 and status['requirement_total']==362
assert status['completed_tasks']==0 and status['accepted_product_requirements']==0
assert status['provider']=='UNAVAILABLE_NOT_ACCEPTED'
assert status['evidence']==dict.fromkeys(('HOST','COMPILE','ANDROID','PROVIDER','PHONE'),'NOT_RUN')
assert status['phone_gates']==dict.fromkeys(('1','2','3','4'),'NOT_RUN')
tasks=task_doc['tasks']
assert len(tasks)==32 and {t['task_code'] for t in tasks}==ids
for task in tasks:
    assert task['phase_id']==(task['step']-1)//4
    src=task['source']
    assert lookup[(src['commit'],src['path'])][src['line']-1]==src['assertion']
    assert set(task['requirement_ids_primary'])=={r['id'] for r in rows if r['primary_task']==task['task_code']}
    assert set(task['requirement_ids_integration'])=={r['id'] for r in rows if task['task_code'] in r['integration_tasks']}
    assert all(task[k] for k in ('inputs','outputs','interfaces','dependencies','error_cancel_recovery','tests','acceptance','evidence_tiers'))
    assert not task['accepted_evidence']
    assert task['status'] in ('IN_PROGRESS','NOT_STARTED')
clean=load('docs/clean-tree-manifest.json')
assert clean['removed_count']==len(clean['paths'])==169
assert len({p['path'] for p in clean['paths']})==169
assert all(p['active_action']=='REMOVE_FROM_NEW_ACTIVE_TREE' for p in clean['paths'])
assert not any(p.suffix in ('.java','.kt','.class','.jar','.py') for p in (R/'reference').rglob('*'))
print(f'PASS documentary integrity: {len(rows)} exact requirements, 32 mapped tasks, {len(manifest)} source-hashed references, 169 clean-tree paths. NOT implementation/runtime acceptance.')
