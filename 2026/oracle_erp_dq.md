# MM

##  커스터머, 인보이스 별 USD, KRW

```sql
select  substr(trx.trx_number, 1, 30) "Trx Number" , 	
    to_char(trx.trx_date, 'YYYY-MM-DD') "Trx Date" , 
     substr(site.location, 1, 10) "Customer Code" , 
     substr(cust.customer_name, 1, 60) "Customer Name" , 
     substr(trx.attribute4, 1, 20) "Invoice No" , 
    to_char(dist.amount, '999,999,999.99' ) "Amount(USD)" , 
    to_char(dist.Acctd_Amount, '99,999,999,999.99' ) "Amount(KRW)" 
FROM  ar_customers cust	
     ,hz_cust_site_uses_all site	
     ,ra_batch_sources_all bat	
     ,ra_cust_trx_line_gl_dist_all dist	
     ,ra_customer_trx_all trx	
WHERE trx.org_id = fnd_profile.value('ORG_ID')  	
AND   cust.customer_id = trx.bill_to_customer_id	
AND   site.site_use_id = trx.bill_to_site_use_id	
AND   bat.batch_source_id = trx.batch_source_id	
AND   dist.customer_trx_id = trx.customer_trx_id	
AND   dist.account_class = 'REC'	
AND   bat.name = 'OPS_INVOICE'	
AND   to_char(trx.trx_date,'YYYYMMDD') BETWEEN '20250101' AND '20250131'	
--20180918 조회조건 추가 강주원 	
AND   substr(site.location, 1, 10) = nvl('',substr(site.location, 1, 10))	
    
order by 1, 2, 3	
```

## 일별 창고 이동 조회

```
select v.subsidiary_code "SUBSIDIARY" , 	
    v.organization_code "ORGANIZATION" , 
    mtt.transaction_type_name "Transaction Type" , 
    to_char(TRUNC(mmt.transaction_date), 'YYYY-MM-DD') "Transaction Type" , 
    msib.segment1 "Item" , 
    SUM(mmt.transaction_quantity) "Quantity" , 
    mmt.transaction_uom "UOM" , 
    mmt.subinventory_code "Subinventory" , 
    mil1.segment1 "Locator" , 
    mmt.transfer_subinventory "To Subinventory" , 
    mil2.segment1 "To Locator" , 
    (SELECT t.class1_name FROM xxpimd_class_tree_v t, mtl_item_catalog_groups_b g WHERE t.leaf_class_id = g.item_catalog_group_id AND g.item_creation_allowed_flag = 'Y' AND g.item_catalog_group_id = msib.item_catalog_group_id) "Class1" , 
    (SELECT t.class2_name FROM xxpimd_class_tree_v t, mtl_item_catalog_groups_b g WHERE t.leaf_class_id = g.item_catalog_group_id AND g.item_creation_allowed_flag = 'Y' AND g.item_catalog_group_id = msib.item_catalog_group_id) "Class2" , 
    (SELECT t.class3_name FROM xxpimd_class_tree_v t, mtl_item_catalog_groups_b g WHERE t.leaf_class_id = g.item_catalog_group_id AND g.item_creation_allowed_flag = 'Y' AND g.item_catalog_group_id = msib.item_catalog_group_id) "Class3" , 
    (SELECT t.class4_name FROM xxpimd_class_tree_v t, mtl_item_catalog_groups_b g WHERE t.leaf_class_id = g.item_catalog_group_id AND g.item_creation_allowed_flag = 'Y' AND g.item_catalog_group_id = msib.item_catalog_group_id) "Class4" , 
    (SELECT t.class5_name FROM xxpimd_class_tree_v t, mtl_item_catalog_groups_b g WHERE t.leaf_class_id = g.item_catalog_group_id AND g.item_creation_allowed_flag = 'Y' AND g.item_catalog_group_id = msib.item_catalog_group_id) "Class5" 
FROM   mtl_material_transactions  mmt	
      ,mtl_system_items_b         msib	
      ,mtl_item_locations         mil1	
      ,mtl_item_locations         mil2	
      ,xxinvm_organization_info_v v	
      ,mtl_transaction_types      mtt	
WHERE  mmt.organization_id = v.organization_id	
AND    mmt.organization_id = msib.organization_id	
AND    mmt.inventory_item_id = msib.inventory_item_id	
AND    mmt.organization_id = mil1.organization_id	
AND    mmt.locator_id = mil1.inventory_location_id	
AND    mmt.organization_id = mil2.organization_id(+)	
AND    mmt.transfer_locator_id = mil2.inventory_location_id(+)	
AND    v.organization_id = mil1.organization_id	
AND    mmt.transaction_type_id = mtt.transaction_type_id	
AND    v.organization_code =   ''	
AND    mmt.subinventory_code = ''	
AND    mmt.transaction_date >= TO_DATE('2025-01', 'YYYY-MM')	
AND    mmt.transaction_date <  ADD_MONTHS(TO_DATE('2025-01', 'YYYY-MM'), 1)	
GROUP BY v.subsidiary_code	
        ,v.organization_code	
        ,mtt.transaction_type_name	
        ,TRUNC(mmt.transaction_date)	
        ,msib.segment1	
        ,mmt.transaction_uom	
        ,mmt.subinventory_code	
        ,mil1.segment1	
        ,mmt.transfer_subinventory	
        ,mil2.segment1	
       ,msib.item_catalog_group_id	
HAVING SUM(mmt.transaction_quantity) <> 0	
```

## 월별 기말 재고 조회

```sql
select xoi.subsidiary_code "Subsidiary" , 	
    xoi.organization_code "ORG" , 
    xoi.biz_group_code "BIZ" , 
    xcs.period_name "Period" , 
    xcs.inventory_group_code "INV GROUP" , 
    xcs.subinventory_code "Subinventory" , 
    mil.segment1 "Locator" , 
    xcs.item_no "Item" , 
    to_char(xcs.ending_onhand_qty) "Ending Onhand" 
FROM   xxinvm_closing_total_summary xcs	
      ,xxinvm_organization_info_v   xoi	
      ,mtl_item_locations           mil	
    
WHERE  xcs.organization_id = xoi.organization_id	
AND    xcs.organization_id = mil.organization_id	
AND    xcs.locator_id = mil.inventory_location_id	
AND    xcs.period_name = '2026-01'	
AND    xoi.subsidiary_code = NVL('', xoi.subsidiary_code)	
AND    xoi.organization_code = NVL('CM1', xoi.organization_code)	
AND    xcs.inventory_group_code = NVL('', xcs.inventory_group_code)	
AND    xcs.subinventory_code IN (SELECT xlv.lookup_code	
                                 FROM   xxlge_lookup_values xlv	
                                 WHERE  xlv.lookup_type = 'XXINVM_PHYSICAL_EXCLUDE_SUBINV'	
                                 AND    xlv.enabled_flag = 'Y'	
                                 UNION ALL	
                                 SELECT 'FGI-NON'	
                                 FROM   dual	
                                 UNION ALL	
                                 SELECT 'MER-NON'	
                                 FROM   dual	
                                 )	
    
ORDER BY 1, 2, 5, 6, 7	
```

## 음수 재고 이력 SUMMARY 조회

```sql
select xctsh.DIVISION "DIVISION" , 	
    xctsh.ORGANIZATION_CODE "ORG" , 
    xctsh.SITE "SITE" , 
    xctsh.INVENTORY_GROUP_CODE "INV_GR" , 
    xctsh.ITEM_NO "ITEM_NO" , 
    xctsh.description "Description" , 
    xctsh.primary_uom_code "UOM" , 
    xctsh.SUBINVENTORY_CODE "SUBINVENTORY" , 
    xctsh.LOCATOR "LOCATOR" , 
    xctsh.INVENTORY_ASSET_FLAG "INV_ASSET_FLAG" , 
    xctsh.A01 "QTY_01" , 
    xctsh.A02 "QTY_02" , 
    xctsh.A03 "QTY_03" , 
    xctsh.A04 "QTY_04" , 
    xctsh.A05 "QTY_05" , 
    xctsh.A06 "QTY_06" , 
    xctsh.A07 "QTY_07" , 
    xctsh.A08 "QTY_08" , 
    xctsh.A09 "QTY_09" , 
    xctsh.A10 "QTY_10" , 
    xctsh.A11 "QTY_11" , 
    xctsh.A12 "QTY_12" , 
    xctsh.A13 "QTY_13" , 
    xctsh.A14 "QTY_14" , 
    xctsh.A15 "QTY_15" , 
    xctsh.A16 "QTY_16" , 
    xctsh.A17 "QTY_17" , 
    xctsh.A18 "QTY_18" , 
    xctsh.A19 "QTY_19" , 
    xctsh.A20 "QTY_20" , 
    xctsh.A21 "QTY_21" , 
    xctsh.A22 "QTY_22" , 
    xctsh.A23 "QTY_23" , 
    xctsh.A24 "QTY_24" , 
    xctsh.A25 "QTY_25" , 
    xctsh.A26 "QTY_26" , 
    xctsh.A27 "QTY_27" , 
    xctsh.A28 "QTY_28" , 
    xctsh.A29 "QTY_29" , 
    xctsh.A30 "QTY_30" , 
    xctsh.A31 "QTY_31" , 
    ROUND(xctsh.A01 * xctsh.COST,0) * xctsh.RATE "AMT_01" , 
    ROUND(xctsh.A02 * xctsh.COST, 0) * xctsh.RATE "AMT_02" , 
    ROUND(xctsh.A03 * xctsh.COST, 0) * xctsh.RATE "AMT_03" , 
    ROUND(xctsh.A04 * xctsh.COST, 0) * xctsh.RATE "AMT_04" , 
    ROUND(xctsh.A05 * xctsh.COST, 0) * xctsh.RATE "AMT_05" , 
    ROUND(xctsh.A06 * xctsh.COST, 0) * xctsh.RATE "AMT_06" , 
    ROUND(xctsh.A07 * xctsh.COST, 0) * xctsh.RATE "AMT_07" , 
    ROUND(xctsh.A08 * xctsh.COST, 0) * xctsh.RATE "AMT_08" , 
    ROUND(xctsh.A09 * xctsh.COST, 0) * xctsh.RATE "AMT_09" , 
    ROUND(xctsh.A10 * xctsh.COST, 0) * xctsh.RATE "AMT_10" , 
    ROUND(xctsh.A11 * xctsh.COST, 0) * xctsh.RATE "AMT_11" , 
    ROUND(xctsh.A12 * xctsh.COST, 0) * xctsh.RATE "AMT_12" , 
    ROUND(xctsh.A13 * xctsh.COST, 0) * xctsh.RATE "AMT_13" , 
    ROUND(xctsh.A14 * xctsh.COST, 0) * xctsh.RATE "AMT_14" , 
    ROUND(xctsh.A15 * xctsh.COST, 0)* xctsh.RATE "AMT_15" , 
    ROUND(xctsh.A16 * xctsh.COST, 0) * xctsh.RATE "AMT_16" , 
    ROUND(xctsh.A17 * xctsh.COST, 0) * xctsh.RATE "AMT_17" , 
    ROUND(xctsh.A18 * xctsh.COST, 0) * xctsh.RATE "AMT_18" , 
    ROUND(xctsh.A19 * xctsh.COST, 0) * xctsh.RATE "AMT_19" , 
    ROUND(xctsh.A20 * xctsh.COST, 0) * xctsh.RATE "AMT_20" , 
    ROUND(xctsh.A21 * xctsh.COST, 0) * xctsh.RATE "AMT_21" , 
    ROUND(xctsh.A22 * xctsh.COST, 0) * xctsh.RATE "AMT_22" , 
    ROUND(xctsh.A23 * xctsh.COST, 0) * xctsh.RATE "AMT_23" , 
    ROUND(xctsh.A24 * xctsh.COST, 0) * xctsh.RATE "AMT_24" , 
    ROUND(xctsh.A25 * xctsh.COST, 0) * xctsh.RATE "AMT_25" , 
    ROUND(xctsh.A26 * xctsh.COST, 0) * xctsh.RATE "AMT_26" , 
    ROUND(xctsh.A27 * xctsh.COST, 0) * xctsh.RATE "AMT_27" , 
    ROUND(xctsh.A28 * xctsh.COST, 0) * xctsh.RATE "AMT_28" , 
    ROUND(xctsh.A29 * xctsh.COST, 0) * xctsh.RATE "AMT_29" , 
    ROUND(xctsh.A30 * xctsh.COST, 0) * xctsh.RATE "AMT_30" , 
    ROUND(xctsh.A31 * xctsh.COST, 0) * xctsh.RATE "AMT_31" , 
    xctsh.TXN_MAX_DATE "TXN_MAX_DATE" , 
    xctsh.PDM_Part_type "PDM_Part_type" 
from   (select /*+ NO_MERGE(T) LEADING(T) */ 	
pf.division ,	
#NAME?	
mps.organization_code ,	
xctsh.organization_id,	
mps.attribute5 site ,	
xctsh.INVENTORY_GROUP_CODE ,	
xctsh.ITEM_NO , 	
msi.primary_uom_code ,	
xctsh.inventory_item_id ,	
msi.description ,	
xctsh.SUBINVENTORY_CODE ,	
mil.segment1 locator ,	
xctsh.INVENTORY_ASSET_FLAG ,	
sum(xctsh.beginning_onhand_qty) beginning_onhand_qty ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '01', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A01 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '02', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A02 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '03', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A03 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '04', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A04 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '05', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A05 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '06', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A06 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '07', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A07 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '08', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A08 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '09', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A09 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '10', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A10 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '11', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A11 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '12', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A12 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '13', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A13 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '14', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A14 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '15', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A15 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '16', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A16 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '17', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A17 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '18', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A18 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '19', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A19 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '20', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A20 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '21', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A21 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '22', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A22 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '23', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A23 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '24', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A24 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '25', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A25 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '26', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A26 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '27', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A27 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '28', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A28 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '29', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A29 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '30', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A30 ,	
SUM( decode( to_char( xctsh.TRANSACTION_DATE, 'DD' ), '31', nvl(xctsh.ENDING_ONHAND_QTY, 0), 0 ) ) as A31 ,	
xxcstf_common_pkg.get_item_twa_cost(xctsh.organization_id, xctsh.inventory_item_id, xctsh.period_name) cost ,	
xxwipm_common_pkg.get_exchange_rate(max(xctsh.TRANSACTION_DATE) , (SELECT xllv.currency_code 	
FROM   xxglf_ledger_le_v xllv	
WHERE  xllv.ledger_id = xxwipm_common_pkg.get_ledger_id(xctsh.organization_id)) , 'KRW' , 'KR_TTM') rate ,	
max(xctsh.TRANSACTION_DATE) TXN_MAX_Date , 	
(SELECT  DECODE(ego.pcb_part_type	
,'A', 'CCL(FCCL포함)'	
,'B', 'PP(Pre-Preg)'	
,'C', 'C/F(Cu-Foil)'	
,'D', 'D/F(Dry Film)'	
,'E', 'OEM'	
,'F', 'INK'	
,'G', '약품류'	
,'H', 'PGC'	
,'I', 'Anode'	
,'J', 'Silver Paste'	
,'K', 'PACKING(포장재)'	
,'L', 'RCC원재료'	
,'M', 'R/F(Release Film)'	
,'N', 'DU보조판(알루미늄, E/B, B/B, D/B)'	
,'O', '기타(재료비성)'	
,'P', 'Mass Lam'	
,'Q', 'Film(D/F, R/F 제외)'	
,'R', 'CP(Copper Plate)'	
,'S', 'CC(Copper Aluminum Copper)'	
,'T', 'CA(Copper Aluminum)'	
,'U', 'AC(Aluminum Copper)'	
,'V', 'CV(COVERAY)'	
,'W', 'BS(BONDING SHEET)'	
,'X', 'ST(SILVER TAPE)'	
,'Y', '탱크약품'	
,'Z', 'SMD'	
,NULL) 	
FROM ego_po_user_spec_ext_agv  ego	
WHERE ego.INVENTORY_ITEM_ID = xctsh.inventory_item_id ) AS PDM_Part_type	
        
from   xxinvm_closing_total_summary_h xctsh ,	
org_organization_definitions ood ,	
mtl_parameters mps ,	
mtl_item_locations mil ,	
mtl_system_items_b  msi ,	
(SELECT pf.division ,	
decode(pf.prod_category, 'FP', 'PB', pf.prod_code) prod_category	
FROM   xxoms_product_family_v pf) pf ,	
(SELECT t.organization_id ,	
t.inventory_group_code,	
t.item_no,	
t.inventory_item_id,	
t.subinventory_code,	
t.locator_id	
FROM   xxinvm_closing_total_summary_h t	
where  t.ENDING_ONHAND_QTY <> 0	
AND    t.PERIOD_NAME = '2025-01'	
AND    t.transaction_date >= TO_DATE('2025-01', 'yyyy-mm')	
AND (( 'Y' = '' AND t.ENDING_ONHAND_QTY < 0 ) OR NVL('', 'N') <> 'Y')	
AND    NOT EXISTS (SELECT xlv.lookup_code	
                            FROM   xxlge_lookup_values xlv	
                            WHERE xlv.module_code = 'XXINVM'	
                            AND    xlv.enabled_flag = 'Y'	
                            AND    xlv.lookup_type = 'XXINVM_NEGATIVE_EXCLUDE_ITEM'	
                            AND    xlv.lookup_code = t.item_no	
)	
GROUP BY t.organization_id ,	
t.inventory_group_code,	
t.item_no,	
t.inventory_item_id, 	
t.subinventory_code,	
t.locator_id	
) t	
where  1=1	
AND    xctsh.organization_id = ood.organization_id	
and    ood.organization_id = mps.organization_id	
AND    mps.attribute3 = pf.prod_category	
AND    xctsh.locator_id = mil.inventory_location_id	
AND    xctsh.organization_id = msi.organization_id 	
AND    xctsh.inventory_item_id = msi.inventory_item_id	
AND    pf.division = nvl('OS', pf.division)	
--AND    mps.attribute1 = nvl('', mps.attribute1)	
AND    xctsh.PERIOD_NAME = '2025-01'	
AND    xctsh.transaction_date >= TO_DATE('2025-01', 'yyyy-mm')	
AND    ood.ORGANIZATION_CODE = nvl('CM1',ood.ORGANIZATION_CODE)	
AND    xctsh.INVENTORY_GROUP_CODE = nvl('',xctsh.INVENTORY_GROUP_CODE)	
AND    xctsh.organization_id = t.organization_id	
AND    xctsh.inventory_group_code = t.inventory_group_code	
AND    xctsh.item_no = t.item_no	
AND    xctsh.subinventory_code = t.subinventory_code	
AND    xctsh.locator_id = t.locator_id	
group by pf.division , mps.organization_code , mps.attribute5 , xctsh.INVENTORY_GROUP_CODE , xctsh.ITEM_NO, xctsh.SUBINVENTORY_CODE , 	
mil.segment1 , xctsh.INVENTORY_ASSET_FLAG , xctsh.organization_id , xctsh.period_name , xctsh.inventory_item_id , msi.description ,msi.primary_uom_code	
) xctsh	
    
order by 1,2,3,4,5,6,7,8	
```

## BOM 대비 실제 출고 차이 조회

```sql
select period_name "Period" , 	
    yyyymmdd "YYYYMMDD" , 
    organization_code "OrgCode" , 
    item_no "ItemNo" , 
    item_description "ItemDescription" , 
    to_char(mon1_twa) "(M1)TWA" , 
    to_char(mon1_incurred_plan_qty) "(M1)Std.IssueQty" , 
    to_char(mon1_others_inout_qty) "(M1)Actual.IssueQty" , 
    to_char(mon1_inv_adjusted_qty) "(M1)Inv.AdjustQty" , 
    to_char(mon1_adjust_qty) "(M1)AdjustQty" , 
    to_char(mon1_adjust_amt) "(M1)AdjustAmount" , 
    to_char(mon1_adjust_rate) "(M1)AdjustRate" , 
    to_char(mon2_twa) "(M2)TWA" , 
    to_char(mon2_incurred_plan_qty) "(M2)Std.IssueQty" , 
    to_char(mon2_others_inout_qty) "(M2)Actual.IssueQty" , 
    to_char(mon2_inv_adjusted_qty) "(M2)Inv.AdjustQty" , 
    to_char(mon2_adjust_qty) "(M2)AdjustQty" , 
    to_char(mon2_adjust_amt) "(M2)AdjustAmount" , 
    to_char(mon2_adjust_rate) "(M2)AdjustRate" , 
    to_char(mon3_twa) "(M3)TWA" , 
    to_char(mon3_incurred_plan_qty) "(M3)Std.IssueQty" , 
    to_char(mon3_others_inout_qty) "(M3)Actual.IssueQty" , 
    to_char(mon3_inv_adjusted_qty) "(M3)Inv.AdjustQty" , 
    to_char(mon3_adjust_qty) "(M3)AdjustQty" , 
    to_char(mon3_adjust_amt) "(M3)AdjustAmount" , 
    to_char(mon3_adjust_rate) "(M3)AdjustRate" , 
    to_char(mon4_twa) "(M4)TWA" , 
    to_char(mon4_incurred_plan_qty) "(M4)Std.IssueQty" , 
    to_char(mon4_others_inout_qty) "(M4)Actual.IssueQty" , 
    to_char(mon4_inv_adjusted_qty) "(M4)Inv.AdjustQty" , 
    to_char(mon4_adjust_qty) "(M4)AdjustQty" , 
    to_char(mon4_adjust_amt) "(M4)AdjustAmount" , 
    to_char(mon4_adjust_rate) "(M4)AdjustRate" , 
    to_char(mon5_twa) "(M5)TWA" , 
    to_char(mon5_incurred_plan_qty) "(M5)Std.IssueQty" , 
    to_char(mon5_others_inout_qty) "(M5)Actual.IssueQty" , 
    to_char(mon5_inv_adjusted_qty) "(M5)Inv.AdjustQty" , 
    to_char(mon5_adjust_qty) "(M5)AdjustQty" , 
    to_char(mon5_adjust_amt) "(M5)AdjustAmount" , 
    to_char(mon5_adjust_rate) "(M5)AdjustRate" , 
    to_char(mon6_twa) "(M6)TWA" , 
    to_char(mon6_incurred_plan_qty) "(M6)Std.IssueQty" , 
    to_char(mon6_others_inout_qty) "(M6)Actual.IssueQty" , 
    to_char(mon6_inv_adjusted_qty) "(M6)Inv.AdjustQty" , 
    to_char(mon6_adjust_qty) "(M6)AdjustQty" , 
    to_char(mon6_adjust_amt) "(M6)AdjustAmount" , 
    to_char(mon6_adjust_rate) "(M6)AdjustRate" , 
    to_char(adjust_rate) "AdjustRate" , 
    pcb_part_type "PartType" , 
    pab_part_type_desc "PartType Desc" , 
    primary_uom_code "UOM" 
FROM  (	
SELECT mp.organization_code	
     , msi.segment1    item_no	
     , msi.description item_description	
     , bomh.period_name	
     , bomh.yyyymmdd	
     , bomh.mon1_twa	
     , bomh.mon1_incurred_plan_qty	
     , bomh.mon1_others_inout_qty	
     , bomh.mon1_inv_adjusted_qty	
     , bomh.mon1_adjust_qty	
     , bomh.mon1_adjust_amt	
     , bomh.mon1_adjust_rate	
     , bomh.mon2_twa	
     , bomh.mon2_incurred_plan_qty	
     , bomh.mon2_others_inout_qty	
     , bomh.mon2_inv_adjusted_qty	
     , bomh.mon2_adjust_qty	
     , bomh.mon2_adjust_amt	
     , bomh.mon2_adjust_rate	
     , bomh.mon3_twa	
     , bomh.mon3_incurred_plan_qty	
     , bomh.mon3_others_inout_qty	
     , bomh.mon3_inv_adjusted_qty	
     , bomh.mon3_adjust_qty	
     , bomh.mon3_adjust_amt	
     , bomh.mon3_adjust_rate	
     , bomh.mon4_twa	
     , bomh.mon4_incurred_plan_qty	
     , bomh.mon4_others_inout_qty	
     , bomh.mon4_inv_adjusted_qty	
     , bomh.mon4_adjust_qty	
     , bomh.mon4_adjust_amt	
     , bomh.mon4_adjust_rate	
     , bomh.mon5_twa	
     , bomh.mon5_incurred_plan_qty	
     , bomh.mon5_others_inout_qty	
     , bomh.mon5_inv_adjusted_qty	
     , bomh.mon5_adjust_qty	
     , bomh.mon5_adjust_amt	
     , bomh.mon5_adjust_rate	
     , bomh.mon6_twa	
     , bomh.mon6_incurred_plan_qty	
     , bomh.mon6_others_inout_qty	
     , bomh.mon6_inv_adjusted_qty	
     , bomh.mon6_adjust_qty	
     , bomh.mon6_adjust_amt	
     , bomh.mon6_adjust_rate	
     , bomh.adjust_rate	
     , pcb.pcb_part_type	
     , (select max(t.description)	
          from fnd_lookup_values t	
         where t.lookup_type = 'XXPLNM_CMDT_CODE3'	
           and t.language = userenv('LANG')	
           and t.meaning = pcb.PCB_PART_TYPE	
       ) pab_part_type_desc	
     , msi.primary_uom_code	
FROM   xxinvm_bom_actual_gap_h bomh	
     , mtl_system_items_b      msi 	
     , mtl_parameters          mp 	
     , ego_po_user_spec_ext_agv pcb	
WHERE  1 = 1	
AND    bomh.organization_id   = mp.organization_id	
AND    bomh.organization_id   = msi.organization_id	
AND    bomh.inventory_item_id = msi.inventory_item_id	
AND    bomh.inventory_item_id = pcb.inventory_item_id(+)	
AND    bomh.period_name       = '2026-01'	
AND    mp.organization_code   = 'CM1'	
AND    bomh.yyyymmdd          = NVL('', bomh.yyyymmdd)	
AND   ('' IS NULL OR msi.segment1              = '')	
)	
order by 1, 2, 3	
```

## 국내 자재 도착 확인

```sql
select (SELECT hls.country FROM hr_organization_units hou, hr_locations hls WHERE hou.location_id = hls.location_id AND hou.organization_id = a.organization_id) "Country Code" , 	
    a.location_code "Location Code" , 
    a.organization_code "Org Code" , 
    a.DEPARTURE_NUMBER "Departuer No" , 
    a.VENDOR_SITE_CODE "Supplier Code" , 
    a.vendor_name "Supplier Name" , 
    a.part_no "Part No" , 
    to_char(a.DEPARTURE_QTY) "Departuer Quantity" , 
    to_char(a.PO_QTY) "PO Quantity" , 
    a.inspection_type "Inspection Type" , 
    a.po_no "PO No" , 
    a.line_type "Line Type" , 
    a.wip_entity_name "WO No" , 
    to_char(a.DEPARTURE_DATE, 'YYYY-MM-DD') "Departure Date" , 
    a.purchaser "Purchaser" , 
    a.subcontract_flag "Subcontract" 
FROM   (	
SELECT rsh.shipment_num                                                      departure_number	
      ,xsv.vendor_site_code                                                  vendor_site_code	
      ,xsv.vendor_name                                                       vendor_name	
      ,msi.segment1                                                          part_no	
      ,rsl.quantity_shipped                                                  departure_qty	
      ,plla.quantity                                                         po_qty	
      ,decode(xxqmm_iqc_inspection_flag_func( rsl.to_organization_id	
                                            , rsl.from_organization_id	
                                            , rsh.vendor_id	
                                            , rsl.item_id	
                                            , rsl.po_line_location_id ),'N','N','Y')      inspection_type	
      ,plla.attribute8                                                                    po_no	
      ,plt.LINE_TYPE                                                                      line_type	
      ,(SELECT wip_entity_name 	
        FROM   wip_entities  we	
        WHERE  we.organization_id = pda.destination_organization_id  	
        AND    we.wip_entity_id = pda.wip_entity_id)                                      wip_entity_name	
      ,rsh.shipped_date                                                                   departure_date	
      ,(SELECT xxinvm_get_purchaser_func(rsl.to_organization_id, rsl.po_line_location_id, rsl.item_id) 	
        FROM   dual)                                                                      purchaser	
      ,DECODE(xsv.subcontract_flag, 'Y', 'Subcontract', xsv.enterprise_sort) subcontract_flag       	
      ,pda.destination_type_code                                                          dist_dest_type_code	
      ,mp.organization_code	
      ,mp.attribute5                                                                      location_code	
      ,mp.organization_id	
FROM   po_headers_ALL                pha	
      ,po_lines_ALL                  pla	
      ,mtl_system_items_b            msi	
      ,xxpom_supplier_v              xsv	
      ,po_line_locations_ALL         plla	
      ,po_distributions_ALL          pda	
      ,rcv_shipment_headers          rsh	
      ,rcv_shipment_lines            rsl	
      ,mtl_parameters                mp	
      ,po_line_types                 plt	
WHERE  rsh.shipment_header_id       = rsl.shipment_header_id	
AND    rsh.vendor_site_id           = xsv.vendor_site_id	
AND    rsh.ship_to_org_id           = mp.organization_id	
AND    rsl.po_line_location_id      = plla.line_location_id	
AND    plla.line_location_id        = pda.line_location_id	
AND    rsl.to_organization_id       = msi.organization_id(+)	
AND    rsl.item_id                  = msi.inventory_item_id(+)	
AND    rsl.po_line_id               = pla.po_line_id	
AND    pla.po_header_id             = pha.po_header_id	
AND    nvl(plla.cancel_flag,'N')    = 'N'	
AND    nvl(plla.closed_code,'OPEN') <> 'FINALLY CLOSED'	
AND    plla.quantity                <> plla.quantity_received	
AND    rsl.quantity_shipped          > rsl.quantity_received	
AND    pla.line_type_id              = plt.line_type_id	
AND    pha.attribute1                <> 'I' -- 2012.05.03	
AND    (	
          plt.attribute5 in ('A','D','P') -- 2012.05.03	
           OR	
         ( plt.attribute5 = 'M' AND NVL(plla.attribute3,'@@@') <> 'SVO' )   --  2012.05.08	
        )	
AND    rsl.quantity_shipped > (SELECT nvl(sum(decode(rti.transaction_type, 'RECEIVE', rti.quantity, rti.quantity * -1)), 0)	
                                 FROM rcv_transactions_interface rti	
                                WHERE rti.shipment_line_id       = rsl.shipment_line_id	
                                  AND rti.transaction_type       in ('RECEIVE', 'RETURN TO VENDOR')	
                                  AND rti.processing_status_code in ('PENDING', 'RUNNING')	
                               )	
AND    mp.organization_code = NVL('CM1', mp.organization_code)	
AND    rsh.shipped_date >= to_date('20260101', 'YYYYMMDD')	
AND    rsh.shipped_date < NVL(to_date('20260131', 'YYYYMMDD'), SYSDATE) + 1	
AND    pda.destination_type_code = NVL('', pda.destination_type_code)	
AND    msi.segment1 = NVL('', msi.segment1)	
AND    xsv.vendor_site_code  = NVL('', xsv.vendor_site_code )	
AND    (DECODE(xxinvm_com_flv_func('XXPOM_PO_TRANSACTION_TYPES_VL', plla.attribute10), NULL	
              ,xxinvm_com_flv_func('XXINVM_SOURCE_TYPE', rsl.source_document_code)	
              ,xxinvm_com_flv_func('XXPOM_PO_TRANSACTION_TYPES_VL', plla.attribute10)) = ''	
        OR '' IS NULL	
        )	
AND    mp.attribute5 = NVL('', mp.attribute5)	
AND    (xxinvm_get_purchaser_func(rsl.to_organization_id, rsl.po_line_location_id, rsl.item_id) = ''	
        OR '' IS NULL	
        )	
AND    (plt.LINE_TYPE = '' OR '' IS NULL)	
UNION ALL	
SELECT rsh.shipment_num                                                      departure_number	
      ,ood.organization_code                                                 vendor_site_code	
      ,ood.organization_name                                                 vendor_name	
      ,msi.segment1                                                          part_no	
      ,rsl.quantity_shipped                                                  departure_qty	
      ,NULL                                                                  po_qty	
      ,'N'                                                                   inspection_type	
      ,NULL                                                                  po_no	
      ,NULL                                                         line_type	
      ,NULL                                                                  wip_entity_name	
      ,rsh.shipped_date                                                      departure_date	
      ,(SELECT xxinvm_get_purchaser_func(rsl.to_organization_id, rsl.po_line_location_id, rsl.item_id) 	
       FROM   dual)                                                             purchaser	
      ,'N'                                                                           subcontract_flag	
      ,rsl.destination_type_code                                             dist_dest_type_code	
      ,mp.organization_code	
      ,mp.attribute5                                                                      location_code	
      ,mp.organization_id	
FROM   mtl_supply                   ms	
      ,rcv_shipment_headers         rsh	
      ,rcv_shipment_lines           rsl	
      ,mtl_system_items             msi	
      ,org_organization_definitions ood	
      ,mtl_parameters               mp	
WHERE  ms.supply_type_code          = 'SHIPMENT'	
AND    rsh.receipt_source_code      = 'INVENTORY'	
AND    ms.to_organization_id        = mp.organization_id	
AND    ms.to_organization_id        = rsl.to_organization_id	
AND    ms.supply_source_id          = rsl.shipment_line_id	
AND    ms.shipment_header_id        = rsh.shipment_header_id	
AND    ms.shipment_line_id          = rsl.shipment_line_id	
AND    rsh.shipment_header_id       = rsl.shipment_header_id	
AND    msi.organization_id(+)       = rsl.to_organization_id	
AND    msi.inventory_item_id(+)     = rsl.item_id	
AND    ood.organization_id          = rsl.from_organization_id	
AND    rsl.to_organization_id       = mp.organization_id	
AND    mp.organization_code = NVL('CM1', mp.organization_code)	
AND    rsh.shipped_date >= to_date('20260101', 'YYYYMMDD')	
AND    rsh.shipped_date < NVL(to_date('20260131', 'YYYYMMDD'), SYSDATE) + 1	
AND    rsl.destination_type_code  = NVL('', rsl.destination_type_code )	
AND    msi.segment1 = NVL('', msi.segment1)	
AND    ood.organization_code   = NVL('', ood.organization_code)	
AND    (xxinvm_com_flv_func('XXINVM_SOURCE_TYPE',rsl.source_document_code) = ''	
        OR '' IS NULL	
        )	
AND    mp.attribute5 = NVL('', mp.attribute5)	
AND    (xxinvm_get_purchaser_func(rsl.to_organization_id, rsl.po_line_location_id, rsl.item_id) = ''	
        OR '' IS NULL	
        )	
AND '' IS NULL	
) a	
order by 1, 2, 3	
```

# FI

## AP 거래처별 계정별 증감 LIST

```sql
select a.ledger_name "Ledger" , 	
	a.segment3 "Account Code" , 
	a.Account_desc "Account Name" , 
	a.vendor_name "Vendor Name" , 
	to_char(a.Aging_M) "Aging(M)" , 
	to_char(sum(last_month_balance_l)) "L/C Amount before" , 
	to_char(decode(sign(sum(a.diff_l)),-1,0, sum(a.diff_l))) "L/C Increase" , 
	to_char(decode(sign(sum(a.diff_l)),-1,sum(a.diff_l)* -1 , 0)) "L/C Decrease" , 
	to_char(sum(this_month_balance_l)) "L/C Amount" , 
	to_char(sum(this_month_balance_f)) "F/C Amount" 
FROM (  	
select 	
xll.ledger_name, 	
  v.segment3  , 	
  xxglf_coa_pkg.get_coa_value_desc_func(v.ledger_id, 'SEGMENT3',v.segment3) Account_desc , 	
  v.vendor_name , 	
  MONTHS_BETWEEN(TO_DATE('2025-12','YYYY-MM'),TRUNC(v.due_date,'MM')) Aging_M , 	
  v.currency_code Currency ,  	
  decode(v.period_name,'2025-12',0,v.accounted_balance) last_month_balance_l , 	
  decode(v.period_name,'2025-12',v.accounted_balance,v.accounted_balance*-1) diff_l,	
  decode(v.period_name,'2025-12',v.accounted_balance,0) this_month_balance_l , 	
  decode(v.period_name,'2025-12',v.entered_balance,0) this_month_balance_f	
FROM     xxacf_dream_balance_detail  v,	
         xxacf_dream_account_v xda,	
         xxglf_ledger_le_v xll	
WHERE    v.ledger_id = xll.ledger_id	
AND      v.ledger_id = xda.ledger_id	
AND      v.segment3 = xda.account_code	
AND      xll.ledger_name = 'LGITKR_LEDGER1'	
AND      v.segment3 >= NVL('',v.segment3)	
AND      v.segment3 <= NVL('',v.segment3)	
AND      xda.use_flag = 'Y'	
AND      xda.balance_detail_flag = 'Y'	
AND      xda.balance_detail_report IN ('Notes_Statement','AP_AR_Statement')	
AND      xda.module_code = 'AP'	
and v.segment1 IN (SELECT  'KM101' from dual UNION ALL	
                   SELECT ffv.flex_value	
                   FROM   fnd_flex_values               ffv	
                         ,fnd_flex_value_norm_hierarchy ffvn	
                   WHERE  ffv.flex_value_set_id = xxglf_coa_pkg.get_coa_value_set_id_func(v.ledger_id,'SEGMENT1')	
                   AND    ffv.summary_flag = 'N'	
                   AND    ffvn.parent_flex_value = 'KM101'  	
                   AND    ffv.flex_value_set_id = ffvn.flex_value_set_id	
                   AND    ffv.flex_value BETWEEN ffvn.child_flex_value_low AND ffvn.child_flex_value_high)	
AND v.period_name IN ('2025-12', to_char(add_months(to_date('2025-12','YYYY-MM'),-1),'yyyy-mm'))	
) a	
GROUP BY a.ledger_name, 	
  a.segment3, 	
  a.Account_desc,	
  a.vendor_name, 	
  a.Aging_M	
	
	
ORDER BY 1, 2, 3, 4, 5	
```

## 휴직자 폐기 카드 대상 LIST=> 조회성 화면=>UAS로 법인카드 이관

```sql
select v.EMPLOYEE_number "사번" , 					
    v.full_name_local "이름" , 				
    v.ORGANIZATION_NAME "부서" , 				
    xcm.corporate_card_no "카드번호" , 				
    to_char(psas.start_date,'YYYY-MM-DD') "휴직시작일" 				
FROM xxgifh_global_employee_v  v,					
     xxccf_card_masters_all xcm,					
     per_secondary_ass_statuses_v psas,					
     per_all_assignments_f        paaf					
WHERE paaf.assignment_id = psas.assignment_id					
 AND xcm.org_id = 108 /* 카드사에 요청을 보내는 한국 법인만 (2024-05-31) */					
 AND v.person_id = paaf.person_id					
 AND v.EMPLOYEE_number = xcm.employee_no					
 AND v.susp_resignation_flag IN ('H')					
 AND xcm.status_code='P'					
 AND paaf.primary_flag = 'Y'					
 AND paaf.assignment_type = 'E'					
 AND psas.user_status LIKE 'Leave%'					
 AND psas.user_status <> 'Leave_Reinstatement' 					
 AND trunc(SYSDATE) BETWEEN paaf.effective_start_date AND					
     paaf.effective_end_date					
 AND psas.start_date <					
     trunc(SYSDATE) -					
     nvl((SELECT nvl(to_number(lookup_code), 0)					
           FROM xxccf_lookup_codes_all					
          WHERE org_id = 108					
            AND lookup_type = 'XXCCF_SUSP_H_APPDAY'					
            AND enabled_flag = 'Y'),					
         90) -- Lookup에 없을경우 90일로 함.					
 AND psas.start_date =					
     (SELECT MAX(start_date) -- 현재날짜 이전의 최종휴직일					
        FROM per_secondary_ass_statuses_v					
       WHERE assignment_id = paaf.assignment_id					
         AND start_date <= TRUNC(SYSDATE)					
         AND user_status LIKE 'Leave%'					
         AND user_status <> 'Leave_Reinstatement') 					
 AND 'Y' IN (SELECT decode(COUNT(1), 0, 'N', 'Y')					
       FROM xxccf_lookup_codes_all					
      WHERE lookup_type = 'XXCCF_PGM_APPLY'					
        AND lookup_code = 'LAIDOFF_EMP_APPLY'					
        AND enabled_flag = 'Y')					
 AND NOT EXISTS (SELECT 'x'					
    FROM xxccf_card_requests_all z					
    WHERE z.request_type_code = 'RT'					
     AND z.status_code IN ('AV', 'C')					
     AND z.employee_no = v.employee_number					
     AND z.card_id = xcm.card_id)					
                    
                    
order by 1, 2, 3					
```

## 법인/월별/계정유형(제조,판,관,연 등)/계정레벨별 당월,전월 기표금액 비교

```sql
select account_code "Account Code" , 	
    account_name "Account Name" , 
    team_code "Dept Code" , 
    team_name "Dept Name" , 
    biz_group "Biz Group" , 
    proj_code "Project Code" , 
    to_char(to_char(base_amt,fnd_currency.get_format_mask(currency_code,30))) "Base Amount" , 
    to_char(to_char(adj_amt,fnd_currency.get_format_mask(currency_code,30))) "Adjust Amount" , 
    to_char(to_char(total_amt,fnd_currency.get_format_mask(currency_code,30))) "Total Amount" , 
    to_char(to_char(pre_base_amt,fnd_currency.get_format_mask(currency_code,30))) "Prev Base Amount" , 
    to_char(to_char(pre_adj_amt,fnd_currency.get_format_mask(currency_code,30))) "Prev Adjust Amount" , 
    to_char(to_char(pre_total_amt,fnd_currency.get_format_mask(currency_code,30))) "Prev Total Amount" , 
    to_char(to_char(add_base_amt,fnd_currency.get_format_mask(currency_code,30))) "Add Base Amount" , 
    to_char(to_char(add_adj_amt,fnd_currency.get_format_mask(currency_code,30))) "Add Adjust Amount" , 
    to_char(to_char(add_tot_amt,fnd_currency.get_format_mask(currency_code,30))) "Add Total Amount" 
FROM(	
SELECT account_code	
      ,account_name	
      ,team_code	
      ,team_name	
      ,biz_group	
      ,proj_code	
      ,currency_code	
      ,base_amt	
      ,adj_amt	
      ,total_amt	
      ,pre_base_amt	
      ,pre_adj_amt	
      ,pre_total_amt	
      ,add_base_amt	
      ,add_adj_amt	
      ,add_tot_amt	
FROM TABLE((xxglf_mfg_cost_pkg.compare_func('LGITKR_LEDGER1'	
                                               ,'2026-02'	
                                               ,'KM101'	
                                               ,'A'	
                                               ,'A') ))	
)	
WHERE 1=1	
    
ORDER BY  1,3,5	
```

## OSP(외주가공비)/OSB(기판용 외주가공비) GL Vs PO 잔액 대사 =>결산검증용

```sql
select tt.biz_group_code "BizGr" , 	
    to_char(SUM(DECODE(tt.flag,'GL',amount,0))) "GL Amount" , 
    to_char(SUM(DECODE(tt.flag,'PO',amount,0))) "PO Amount" , 
    to_char(SUM(DECODE(tt.flag,'GL',amount,0))-SUM(DECODE(tt.flag,'PO',amount,0))) "GAP" 
FROM (SELECT /*+ LEADING(Y) USE_NL(X) INDEX(X GL_BALANCES_N1) */ 'GL' AS flag	
      ,y.segment1 AS biz_group_code	
      ,SUM((NVL(x.period_net_dr,0)-NVL(x.period_net_cr,0))) AS amount	
FROM gl_balances x	
    ,gl_code_combinations y	
WHERE x.code_combination_id = y.code_combination_id	
AND   x.period_name = '2025-12'	
AND   x.ledger_id = fnd_profile.value('GL_SET_OF_BKS_ID')	
AND   nvl(x.translated_flag, 'X') <> 'R'	
AND   y.segment1 IN (SELECT xbgl.biz_group_code	
                         FROM   xxcstf_biz_group_level_v xbgl	
                         WHERE  xbgl.value_set_id = xxglf_coa_pkg.get_coa_value_set_id_func(fnd_profile.value('GL_SET_OF_BKS_ID'), 'SEGMENT1')	
                         AND    xbgl.parent_biz_group_code = NVL('KM101', xbgl.parent_biz_group_code)	
                         AND    xbgl.ledger_id = fnd_profile.value('GL_SET_OF_BKS_ID'))	
AND   y.segment3 in ('73590101' ,'73590102', '72040101')	
GROUP BY y.segment1	
UNION ALL	
SELECT 'PO' AS flag	
      ,xpl.biz_group AS biz_group_code	
      ,sum(nvl(xpl.func_curr_amount, 0))  AS amount	
FROM   xxpom_payables_headers xph	
      ,xxpom_payables_lines   xpl	
      ,gl_code_combinations   gcc	
WHERE  1 = 1	
AND    xph.period_name = '2025-12'	
AND    xph.header_id = xpl.header_id	
AND    xpl.line_type_code = 'OSP'	
AND    xph.ap_invoice_no IS NOT NULL	
AND    xph.approval_status_code = 'APPROVED'	
AND    xpl.dr_ccid = gcc.code_combination_id	
AND    xpl.biz_group IN (SELECT xbgl.biz_group_code	
                         FROM   xxcstf_biz_group_level_v xbgl	
                         WHERE  xbgl.value_set_id = xxglf_coa_pkg.get_coa_value_set_id_func(fnd_profile.value('GL_SET_OF_BKS_ID'), 'SEGMENT1')	
                         AND    xbgl.parent_biz_group_code = NVL('KM101', xbgl.parent_biz_group_code)	
                         AND    xbgl.ledger_id = fnd_profile.value('GL_SET_OF_BKS_ID'))	
GROUP BY xpl.biz_group	
UNION ALL	
SELECT /*+ NO_MERGE(CGA) USE_HASH(CGA) SWAP_JOIN_INPUTS(CGA) */ 'PO' AS flag 	
      ,xpl.biz_group	
      ,sum(xpl.func_curr_amount)   AS processing_amount	
FROM   xxpom_payables_headers     xph	
      ,xxpom_payables_lines       xpl	
      ,po_distributions_all       pd	
      ,bom_departments            bd	
      ,mtl_system_items_b         msib	
      ,xxcstf_cost_group_assign_v cga	
--,      gl_code_combinations      gcc	
WHERE  xph.period_name = '2025-12'	
AND    xpl.biz_group IN (SELECT xbgl.biz_group_code	
                         FROM   xxcstf_biz_group_level_v xbgl	
                         WHERE  xbgl.value_set_id = xxglf_coa_pkg.get_coa_value_set_id_func(fnd_profile.value('GL_SET_OF_BKS_ID'), 'SEGMENT1')	
                         AND    xbgl.parent_biz_group_code = NVL('KM101', xbgl.parent_biz_group_code)	
                         AND    xbgl.ledger_id = fnd_profile.value('GL_SET_OF_BKS_ID'))	
AND    xph.header_id = xpl.header_id	
AND    xpl.line_type_code = 'OSB'	
AND    xph.ap_invoice_no IS NOT NULL	
AND    xph.approval_status_code = 'APPROVED'	
AND    xpl.po_line_location_id = pd.line_location_id	
AND    xpl.biz_group = cga.biz_group_code	
AND    cga.ledger_id = fnd_profile.value('GL_SET_OF_BKS_ID')	
AND    cga.organization_id = msib.organization_id	
AND    cga.organization_id = bd.organization_id	
AND    pd.attribute15 = msib.segment1	
AND    pd.attribute13 = bd.department_code	
GROUP BY xpl.biz_group) tt	
HAVING SUM(DECODE(tt.flag,'GL',amount,0))-SUM(DECODE(tt.flag,'PO',amount,0)) <> 0	
GROUP BY biz_group_code	
    
order by 1	
```

## TTM 환율 변경 내역

```sql
select USER_CONVERSION_TYPE "Conversion Type" , 	
    to_char(CONVERSION_DATE, 'YYYY-MM-DD') "Conversion Date" , 
    FROM_CURRENCY "From Currency" , 
    TO_CURRENCY "To Currency" , 
    to_char(PREV_RATE) "Prev Ex Rate" , 
    to_char(PREV_UPDATE_DATE, 'YYYY-MM-DD HH24:MI:SS') "Prev Update Date" , 
    to_char(UPDATED_CONVERSION_RATE) "Updated Ex Rate" , 
    to_char(LAST_UPDATE_DATE, 'YYYY-MM-DD HH24:MI:SS') "Last Updated Date" , 
    UPDATED_BY "Updated By" , 
    to_char(PRESENT_CONVERSION_RATE) "Present Ex Rate" , 
    UPDATE_TYPE "Update Type" 
FROM (	
SELECT a.user_conversion_type, decode(a.eai_data_crud_cd,'C','Creation','U','Update','D','Delete') as update_type, a.conversion_date, a.from_currency, a.to_currency	
     , lead(a.conversion_rate) over (partition by a.conversion_date,a.user_conversion_type, a.from_currency, a.to_currency	
                                     order by a.last_update_date desc, a.eai_row_id desc  ) as  prev_rate	
     , lead(a.last_update_date) over (partition by a.conversion_date,a.user_conversion_type, a.from_currency, a.to_currency	
                                     order by a.last_update_date desc, a.eai_row_id desc  ) as  prev_update_date                                     	
     , a.conversion_rate as updated_conversion_rate	
     , a.last_update_date, fu_u.description as updated_by	
     , gdr.conversion_rate as present_conversion_rate	
     , a.eai_row_id, a.eai_transfer_flag1 as mail_flag	
  FROM xxglf_erp_gl_daily_rates_s_if a	
      ,fnd_user fu_c	
      ,fnd_user fu_u    	
      ,gl_daily_rates_v gdr  	
WHERE 1=1	
AND a.created_by = fu_c.user_id (+)	
AND a.last_updated_by = fu_u.user_id (+)	
AND a.user_conversion_type = gdr.user_conversion_type (+)	
AND a.conversion_date = gdr.conversion_date (+)	
AND a.from_currency = gdr.from_currency (+)	
AND a.to_currency = gdr.to_currency (+)	
AND (a.user_conversion_type, a.conversion_date, a.from_currency, a.to_currency) in 	
      (SELECT b.user_conversion_type, b.conversion_date, b.from_currency, b.to_currency FROM xxglf_erp_gl_daily_rates_s_if b 	
       WHERE  b.eai_data_crud_cd in ('U','D')	
       AND b.user_conversion_type like '___TTM'	
       AND b.user_conversion_type = NVL('KR_TTM',b.user_conversion_type)	
       AND b.conversion_date >= NVL(to_date('','yyyy-mm-dd'),b.conversion_date)	
       AND b.conversion_date <= NVL(to_date('','yyyy-mm-dd'),b.conversion_date)       	
      )	
    
) t	
WHERE decode(t.update_type,'Delete',-999,nvl(t.prev_rate,-999)) != t.updated_conversion_rate	
AND t.update_type != 'Creation'	
AND NVL(t.mail_flag,'N') = NVL('',NVL(t.mail_flag,'N'))	
    
order by 2 desc,1, 3, 4, 8 desc, t.eai_row_id desc	
```
