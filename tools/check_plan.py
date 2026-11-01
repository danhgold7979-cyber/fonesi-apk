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
assert task_doc['phases']==8 and task_doc['tasks_total']==32 and task_doc['completed_count']==5
assert task_doc['phone_gates']=={'1':[9,10,11,12],'2':[17,18,19,20],'3':[21,22,23,24],'4':[29,30,31,32]}
status=load('docs/status.json')
assert status['phase_total']==8 and status['task_total']==32 and status['requirement_total']==362
assert status['completed_tasks']==5 and status['accepted_product_requirements']==0
assert status['provider']=='UNAVAILABLE_NOT_ACCEPTED'
assert status['evidence']=={'HOST':'PASS_FOUNDATION_ONLY','COMPILE':'PASS_FOUNDATION_ONLY','ANDROID':'NOT_RUN','PROVIDER':'NOT_RUN','PHONE':'NOT_RUN'}
assert status['phone_gates']==dict.fromkeys(('1','2','3','4'),'NOT_RUN')
registry_path=R/'docs/evidence/documentary-acceptance.json'
assert hashlib.sha256(registry_path.read_bytes()).hexdigest()=='db0904f9d49889d63549b11aa0172ccdbf05cbd42d969a8ebd5d799f61a16848'
registry=load('docs/evidence/documentary-acceptance.json')
assert hashlib.sha256((R/registry['independent_review']['path']).read_bytes()).hexdigest()==registry['independent_review']['sha256']
assert registry['accepted_tasks']==['APK-P0-T01','APK-P0-T02']
assert status['documentary_acceptance_registry']=='docs/evidence/documentary-acceptance.json'
assert status['documentary_progress']=={'phase0_accepted':2,'phase0_total':4,'phase0_task_percent':50.0,'all_accepted':2,'all_total':32,'all_task_percent':6.25,'scope':'DOCUMENTARY_TASKS_ONLY_NOT_PRODUCT_FUNCTIONALITY'}
foundation_path='docs/evidence/foundation-acceptance.json'
assert hashlib.sha256((R/foundation_path).read_bytes()).hexdigest()=='cfc840006a30258f95e6a57ce55f54a310df803bb909131358018f7b8c55f0ea'
foundation=load(foundation_path)
assert hashlib.sha256((R/foundation['independent_review']['path']).read_bytes()).hexdigest()==foundation['independent_review']['sha256']
assert foundation['accepted_tasks']==['APK-P0-T03','APK-P0-T04']
assert status['foundation_acceptance_registry']==foundation_path
assert status['evidence']==foundation['evidence']
assert status['task_progress']=={'phase0_accepted':4,'phase0_total':4,'phase0_task_percent':100.0,'all_accepted':5,'all_total':32,'all_task_percent':15.625,'scope':'TASK_COUNTS_ONLY_NOT_PRODUCT_FUNCTIONALITY','phase1_accepted':1,'phase1_total':4,'phase1_task_percent':25.0}
inventory_path='docs/evidence/provider-inventory-acceptance.json'
assert hashlib.sha256((R/inventory_path).read_bytes()).hexdigest()=='5c390f309926e6a55f92ff3adf606e0a0ab03e5390212f64fa55a1d548eb1a94'
inventory=load(inventory_path)
assert status['provider_inventory_acceptance_registry']==inventory_path
for key in ('independent_review','candidate_inventory'):
    item=inventory[key]
    assert hashlib.sha256((R/item['path']).read_bytes()).hexdigest()==item['sha256']
candidates=load(inventory['candidate_inventory']['path'])
assert len(candidates['candidates'])==6 and all(not c['eligible'] and c['activation']=='BLOCKED' and c['test_result']=='NOT_RUN' for c in candidates['candidates'])
assert set(candidates['app_compatibility'])=={'Facebook','Messenger','Zalo','TikTok','Threads','X','Shopee','YouTube'}
assert set(candidates['app_compatibility'].values())=={'UNKNOWN'}
tasks=task_doc['tasks']
assert len(tasks)==32 and {t['task_code'] for t in tasks}==ids
for task in tasks:
    assert task['phase_id']==(task['step']-1)//4
    src=task['source']
    assert lookup[(src['commit'],src['path'])][src['line']-1]==src['assertion']
    assert set(task['requirement_ids_primary'])=={r['id'] for r in rows if r['primary_task']==task['task_code']}
    assert set(task['requirement_ids_integration'])=={r['id'] for r in rows if task['task_code'] in r['integration_tasks']}
    assert all(task[k] for k in ('inputs','outputs','interfaces','dependencies','error_cancel_recovery','tests','acceptance','evidence_tiers'))
    if task['task_code'] in registry['accepted_tasks']:
        assert task['status']=='ACCEPTED_DOCUMENTARY'
        assert task['accepted_evidence']==['docs/evidence/documentary-acceptance.json#'+task['task_code']]
    elif task['task_code'] in foundation['accepted_tasks']:
        assert task['status']=='ACCEPTED_HOST_COMPILE'
        assert task['accepted_evidence']==[foundation_path+'#'+task['task_code']]
    elif task['task_code'] in inventory['accepted_tasks']:
        assert task['status']=='ACCEPTED_DOCUMENTARY'
        assert task['accepted_evidence']==[inventory_path+'#'+task['task_code']]
    elif task['task_code'] in inventory['blocked_tasks']:
        assert task['status']=='BLOCKED' and not task['accepted_evidence']
    else:
        assert not task['accepted_evidence']
        assert task['status'] in ('IN_PROGRESS','NOT_STARTED')
clean=load('docs/clean-tree-manifest.json')
assert clean['removed_count']==len(clean['paths'])==169
assert len({p['path'] for p in clean['paths']})==169
assert all(p['active_action']=='REMOVE_FROM_NEW_ACTIVE_TREE' for p in clean['paths'])
assert not any(p.suffix in ('.java','.kt','.class','.jar','.py') for p in (R/'reference').rglob('*'))
print(f'PASS documentary integrity: {len(rows)} exact requirements, 32 mapped tasks, {len(manifest)} source-hashed references, 169 clean-tree paths. NOT implementation/runtime acceptance.')
