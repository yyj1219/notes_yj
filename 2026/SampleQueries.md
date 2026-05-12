# C0 레벨 쿼리

## 1. 단일 테이블 DIMENSION View 생성

```
@Analytics.dataCategory: #DIMENSION
@EndUserText.label: 'Company Code Dimension'

define view entity ZI_CompanyCodeDim
  as select from I_CompanyCode 
{
  key CompanyCode,
      CompanyName,
      Currency,
      Country
}
```

## 2. 기존 CDS에 필드 1~2개 추가

```
-- 기존 ZI_CompanyCodeDim 에 필드 추가
@Analytics.dataCategory: #DIMENSION
@EndUserText.label: 'Company Code Dimension'

define view entity ZI_CompanyCodeDim
as select from I_CompanyCode 
{
  key CompanyCode,
      CompanyName,
      Currency,
      Country

      -- ✅ 추가 필드 1
      adrnr as AddressNumber,
      -- ✅ 추가 필드 2
      ktopl as ChartOfAccounts
}
```

## 3. 표준 CDS 복사 후 필터만 추가

```
-- SAP 표준 I_CompanyCode 복사 후 한국(KR) 필터 추가
@Analytics.dataCategory: #DIMENSION
@EndUserText.label: 'Company Code Dimension KR'

define view entity ZI_CompanyCodeDimKR
  as select from I_CompanyCode 
{
  key CompanyCode,
      CompanyName,
      Currency,
      Country
}
-- ✅ 필터만 추가
where land1 = 'KR'
```

# C1 레벨 쿼리

## 4. 2개 CDS join DIMENSION View

```
-- I_Customer + I_CustomerSalesArea Join

@Analytics.dataCategory: #DIMENSION
@EndUserText.label: 'Customer Dimension'

define view entity ZI_CustomerDim
  as select from I_Customer
    inner join I_CustomerSalesArea
      on I_Customer.Customer = I_CustomerSalesArea.Customer
{
  key I_Customer.Customer,
      I_Customer.CustomerName,
      I_Customer.Country,
      I_Customer.City,

      -- ✅ 2번째 CDS 필드
      I_CustomerSalesArea.SalesOrganization,
      I_CustomerSalesArea.CustomerGroup,

      -- 인라인 계산
      cast( I_Customer.Industry as abap.char(4) )
                              as IndustryCode
}
```

## 5. 기본 Measure만 있는 단순 CUBE View

```
-- I_SalesOrder 기반 단순 CUBE

@Analytics.dataCategory: #CUBE
@EndUserText.label: 'Sales Order Cube Simple'

define view entity ZI_SalesOrderCubeSimple
  as select from I_SalesOrder
{
  key SalesOrder,
      SoldToParty         as Customer,
      SalesOrganization   as SalesOrg,
      CreationDate        as OrderDate,
      TransactionCurrency as Currency,

      -- ✅ 기본 Measure
      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'Currency'
      TotalNetAmount      as NetAmount,

      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'Currency'
      TaxAmount
}
```

## 6. Restricted Measure 없는 기본 QUERY View

```
-- ZI_SalesOrderCubeSimple 기반 기본 QUERY

@AccessControl.authorizationCheck: #NOT_ALLOWED
@EndUserText.label: 'Sales Order Query Simple'

define transient view entity ZQ_SalesOrderQuerySimple
  provider contract analytical_query
  as projection on ZI_SalesOrderCubeSimple
{
  -- ✅ Dimension
  @AnalyticsDetails.query.axis: #ROWS
  SalesOrder,
  Customer,
  SalesOrg,
  OrderDate,

  -- ✅ Measure (Restricted 없음)
  @AnalyticsDetails.query.axis: #COLUMNS
  NetAmount,
  TaxAmount,
  Currency
}
```

# C2 레벨 쿼리

## 7. 3개 CDS Join DIMENSION View

```
-- I_Product + I_ProductDescription + I_ProductGroup Join

@Analytics.dataCategory: #DIMENSION
@EndUserText.label: 'Material Dimension'

define view entity ZI_MaterialDim
  as select from I_Product
    inner join I_ProductDescription
      on  I_Product.Product  = I_ProductDescription.Product
      and I_ProductDescription.Language
                             = $session.system_language
    left outer join I_ProductGroup
      on I_Product.ProductGroup = I_ProductGroup.ProductGroup
{
  key I_Product.Product,
      I_ProductDescription.ProductDescription  as MaterialName,
      I_Product.ProductGroup,

      -- ✅ 3번째 CDS 필드
      I_ProductGroup.ProductGroupName,

      I_Product.ProductType,
      I_Product.BaseUnit,

      -- 인라인 계산
      case I_Product.ProductType
        when 'FERT' then '완제품'
        when 'ROH'  then '원자재'
        else             '기타'
      end                                      as ProductTypeText
}
```

## 8. 기본 Measure만 있는 단순 CUBE View

```
-- I_PurchaseOrder + I_PurchaseOrderItem + I_Supplier Join

@Analytics.dataCategory: #CUBE
@EndUserText.label: 'Purchase Order Cube'

define view entity ZI_PurchaseOrderCube
  as select from I_PurchaseOrder
    inner join I_PurchaseOrderItem
      on I_PurchaseOrder.PurchaseOrder
       = I_PurchaseOrderItem.PurchaseOrder
    left outer join I_Supplier
      on I_PurchaseOrder.Supplier
       = I_Supplier.Supplier
{
  key I_PurchaseOrder.PurchaseOrder,
      I_PurchaseOrder.Supplier,
      I_Supplier.SupplierName,
      I_PurchaseOrder.PurchasingOrganization as PurchOrg,
      I_PurchaseOrder.CreationDate           as OrderDate,
      I_PurchaseOrderItem.Material,
      I_PurchaseOrder.DocumentCurrency       as Currency,

      -- ✅ 기본 Measure
      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'Currency'
      I_PurchaseOrderItem.NetAmount,

      @DefaultAggregation: #SUM
      @Semantics.quantity.unitOfMeasure: 'OrderUnit'
      I_PurchaseOrderItem.OrderQuantity      as OrderQty,

      I_PurchaseOrderItem.PurchaseOrderQuantityUnit as OrderUnit
}
```

## 9. Restricted Measure 없는 기본 QUERY View

```
-- ZI_PurchaseOrderCube 기반 기본 QUERY

@AccessControl.authorizationCheck: #NOT_ALLOWED
@EndUserText.label: 'Purchase Order Query'

define transient view entity ZQ_PurchaseOrderQuery
  provider contract analytical_query
  as projection on ZI_PurchaseOrderCube
{
  @AnalyticsDetails.query.axis: #ROWS
  PurchaseOrder,
  Supplier,
  SupplierName,
  PurchOrg,
  OrderDate,
  Material,

  @AnalyticsDetails.query.axis: #COLUMNS
  NetAmount,
  OrderQty,
  Currency,
  OrderUnit
}
```

# C3 레벨 쿼리

## 10. 일반적인 영업/구매 CUBE View

```
-- I_SalesOrder + I_SalesOrderItem
-- + I_SalesOrderScheduleLine + I_Customer Join

@Analytics.dataCategory: #CUBE
@EndUserText.label: 'Sales Revenue Cube'

define view entity ZI_SalesRevenueCube
  as select from I_SalesOrder
    inner join I_SalesOrderItem
      on I_SalesOrder.SalesOrder
       = I_SalesOrderItem.SalesOrder
    inner join I_SalesOrderScheduleLine
      on  I_SalesOrderItem.SalesOrder
        = I_SalesOrderScheduleLine.SalesOrder
      and I_SalesOrderItem.SalesOrderItem
        = I_SalesOrderScheduleLine.SalesOrderItem
    left outer join I_Customer
      on I_SalesOrder.SoldToParty = I_Customer.Customer
{
  key I_SalesOrder.SalesOrder,
      I_SalesOrder.SoldToParty           as Customer,
      I_Customer.CustomerName,
      I_SalesOrder.SalesOrganization     as SalesOrg,
      I_SalesOrderItem.Material,
      I_SalesOrder.CreationDate          as OrderDate,
      I_SalesOrder.FiscalYear,
      I_SalesOrder.TransactionCurrency   as Currency,

      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'Currency'
      I_SalesOrderItem.NetAmount,

      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'Currency'
      I_SalesOrderItem.TaxAmount,

      -- 인라인 계산
      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'Currency'
      I_SalesOrderItem.NetAmount
        + I_SalesOrderItem.TaxAmount     as GrossAmount,

      -- Exception Aggregation
      @DefaultAggregation: #SUM
      @Aggregation.exceptionAggregationSteps: [{
        exceptionAggregationBehavior: #COUNT_DISTINCT,
        exceptionAggregationElements: ['Customer']
      }]
      cast( 0 as abap.int4 )             as UniqueCustomers,

      @DefaultAggregation: #SUM
      @Aggregation.exceptionAggregationSteps: [{
        exceptionAggregationBehavior: #MAX,
        exceptionAggregationElements: ['OrderDate']
      }]
      cast( dats'00000000' as abap.dats ) as LastOrderDate
}
```

## 11. Formula 포함 QUERY View

```
-- ZI_SalesRevenueCube 기반 Formula QUERY

@AccessControl.authorizationCheck: #NOT_ALLOWED
@EndUserText.label: 'Sales Revenue Query'

define transient view entity ZQ_SalesRevenueQuery
  provider contract analytical_query
  as projection on ZI_SalesRevenueCube
{
  @AnalyticsDetails.query.axis: #ROWS
  Customer,
  CustomerName,
  SalesOrg,
  Material,
  FiscalYear,

  @AnalyticsDetails.query.axis: #COLUMNS
  NetAmount,
  TaxAmount,
  GrossAmount,
  UniqueCustomers,
  LastOrderDate,
  Currency,

  -- ✅ Formula 1 — 세율
  @Aggregation.default: #FORMULA
  $projection.TaxAmount / $projection.NetAmount * 100
                as TaxRate,

  -- ✅ Formula 2 — 고객 1인당 평균 매출
  @Aggregation.default: #FORMULA
  $projection.NetAmount / $projection.UniqueCustomers
                as RevenuePerCustomer
}
```

# C4 레벨 쿼리

## 13. 수익성 분석 CUBE View (다중 팩트 Join)

```
-- I_SalesOrder + I_SalesOrderItem
-- + I_Customer + I_CustomerSalesArea + I_Product Join

@Analytics.dataCategory: #CUBE
@EndUserText.label: 'Profitability Cube'

define view entity ZI_ProfitabilityCube
  with parameters
    P_DisplayCurrency : abap.cuky,
    P_RateDate        : abap.dats

  as select from I_SalesOrder
    inner join I_SalesOrderItem
      on  I_SalesOrder.SalesOrder
        = I_SalesOrderItem.SalesOrder
    left outer join I_Customer
      on  I_SalesOrder.SoldToParty
        = I_Customer.Customer
    left outer join I_CustomerSalesArea
      on  I_Customer.Customer
        = I_CustomerSalesArea.Customer
      and I_CustomerSalesArea.SalesOrganization
        = I_SalesOrder.SalesOrganization
    left outer join I_Product
      on  I_SalesOrderItem.Material
        = I_Product.Product
{
  key I_SalesOrder.SalesOrder,
      I_SalesOrder.SoldToParty           as Customer,
      I_Customer.CustomerName,
      I_CustomerSalesArea.CustomerGroup,
      I_SalesOrder.SalesOrganization     as SalesOrg,
      I_SalesOrderItem.Material,
      I_Product.ProductGroup,
      I_SalesOrder.CreationDate          as OrderDate,
      I_SalesOrder.FiscalYear,
      I_SalesOrder.TransactionCurrency   as Currency,

      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'Currency'
      I_SalesOrderItem.NetAmount         as Revenue,

      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'Currency'
      I_SalesOrderItem.CostAmount        as COGS,

      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'Currency'
      I_SalesOrderItem.NetAmount
        - I_SalesOrderItem.CostAmount    as GrossProfit,

      -- Currency Conversion
      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'DisplayCurrency'
      currency_conversion(
        amount             => I_SalesOrderItem.NetAmount,
        source_currency    => I_SalesOrder.TransactionCurrency,
        target_currency    => :P_DisplayCurrency,
        exchange_rate_date => :P_RateDate,
        exchange_rate_type => 'M',
        error_handling     => 'SET_TO_NULL'
      )                                  as ConvertedRevenue,

      @Semantics.currencyCode: true
      :P_DisplayCurrency                 as DisplayCurrency,

      -- Exception Aggregation
      @DefaultAggregation: #SUM
      @Aggregation.exceptionAggregationSteps: [{
        exceptionAggregationBehavior: #COUNT_DISTINCT,
        exceptionAggregationElements: ['Customer']
      }]
      cast( 0 as abap.int4 )             as UniqueCustomers,

      @DefaultAggregation: #SUM
      @Aggregation.exceptionAggregationSteps: [{
        exceptionAggregationBehavior: #MAX,
        exceptionAggregationElements: ['OrderDate']
      }]
      cast( dats'00000000' as abap.dats ) as LastOrderDate,

      -- Virtual Element (DB 조회 포함)
      @ObjectModel.virtualElement: true
      @ObjectModel.virtualElementCalculatedBy: 'ABAP:ZCL_PROFIT_VIRT'
      cast( '' as abap.char(10) )         as CreditGrade
}
```

## 15.전년 대비 비교 QUERY View

```
-- ZI_ProfitabilityCube 기반 전년 비교 QUERY

@AccessControl.authorizationCheck: #NOT_ALLOWED
@EndUserText.label: 'Profitability YoY Query'

define transient view entity ZQ_ProfitYoYQuery
  provider contract analytical_query
  as projection on ZI_ProfitabilityCube
{
  @AnalyticsDetails.query.axis: #ROWS
  Customer,
  CustomerName,
  CustomerGroup,
  SalesOrg,
  Material,
  FiscalYear,
  CreditGrade,

  @AnalyticsDetails.query.axis: #COLUMNS
  Revenue,
  GrossProfit,
  ConvertedRevenue,
  UniqueCustomers,
  LastOrderDate,
  DisplayCurrency,

  -- ✅ Restricted — 전년 매출
  @AnalyticsDetails.query.formula:
    '$restricted( $projection.Revenue, FiscalYear = $prevyear )'
  cast( 0 as abap.dec(15,2) ) as PrevYearRevenue,

  -- ✅ Restricted — 전년 이익
  @AnalyticsDetails.query.formula:
    '$restricted( $projection.GrossProfit, FiscalYear = $prevyear )'
  cast( 0 as abap.dec(15,2) ) as PrevYearGrossProfit,

  -- ✅ Formula — 매출 증감률
  @Aggregation.default: #FORMULA
  ( $projection.Revenue - $projection.PrevYearRevenue )
    / $projection.PrevYearRevenue * 100
                as RevenueYoYRate,

  -- ✅ Formula — 총이익률
  @Aggregation.default: #FORMULA
  $projection.GrossProfit / $projection.Revenue * 100
                as GrossProfitRate,

  -- ✅ Formula — 이익 증감률
  @Aggregation.default: #FORMULA
  ( $projection.GrossProfit - $projection.PrevYearGrossProfit )
    / $projection.PrevYearGrossProfit * 100
                as GrossProfitYoYRate
}
```

# C5 레벨 쿼리

## 16. 다차원 수익성 전체 분석 모델 신규 구축

```
-- [DIMENSION 1 — 고객]
@Analytics.dataCategory: #DIMENSION
define view entity ZI_Dim_Customer
  as select from I_Customer
    inner join I_CustomerSalesArea
      on  I_Customer.Customer
        = I_CustomerSalesArea.Customer
    left outer join I_Country
      on  I_Customer.Country
        = I_Country.Country
{
  key I_Customer.Customer,
      I_Customer.CustomerName,
      I_Customer.Country,
      I_Country.CountryName,
      I_CustomerSalesArea.CustomerGroup,
      I_CustomerSalesArea.SalesOffice
}

-- [DIMENSION 2 — 자재]
@Analytics.dataCategory: #DIMENSION
define view entity ZI_Dim_Material
  as select from I_Product
    inner join I_ProductDescription
      on  I_Product.Product
        = I_ProductDescription.Product
      and I_ProductDescription.Language
        = $session.system_language
    left outer join I_ProductGroupText
      on  I_Product.ProductGroup
        = I_ProductGroupText.ProductGroup
      and I_ProductGroupText.Language
        = $session.system_language
{
  key I_Product.Product            as Material,
      I_ProductDescription.ProductDescription as MaterialName,
      I_Product.ProductGroup,
      I_ProductGroupText.ProductGroupName,
      I_Product.ProductType,
      I_Product.BaseUnit
}

-- [DIMENSION 3 — 회사/조직]
@Analytics.dataCategory: #DIMENSION
define view entity ZI_Dim_Company
  as select from I_CompanyCode
    inner join I_SalesOrganization
      on  I_CompanyCode.CompanyCode
        = I_SalesOrganization.CompanyCode
{
  key I_CompanyCode.CompanyCode,
      I_CompanyCode.CompanyCodeName,
      I_SalesOrganization.SalesOrganization as SalesOrg,
      I_CompanyCode.Currency                as CompanyCurrency
}

-- [CUBE View — 전체 수익성]
@Analytics.dataCategory: #CUBE
define view entity ZI_ProfitFullCube
  with parameters
    P_DisplayCurrency : abap.cuky,
    P_RateDate        : abap.dats

  as select from I_SalesOrder
    inner join I_SalesOrderItem
      on  I_SalesOrder.SalesOrder
        = I_SalesOrderItem.SalesOrder
    left outer join I_Customer
      on  I_SalesOrder.SoldToParty
        = I_Customer.Customer
    left outer join I_CustomerSalesArea
      on  I_Customer.Customer
        = I_CustomerSalesArea.Customer
      and I_CustomerSalesArea.SalesOrganization
        = I_SalesOrder.SalesOrganization
    left outer join I_Product
      on  I_SalesOrderItem.Material
        = I_Product.Product
{
  key I_SalesOrder.SalesOrder,
      I_SalesOrder.SoldToParty           as Customer,
      I_Customer.CustomerName,
      I_CustomerSalesArea.CustomerGroup,
      I_SalesOrder.SalesOrganization     as SalesOrg,
      I_SalesOrder.CompanyCode,
      I_SalesOrderItem.Material,
      I_Product.ProductGroup             as MaterialGroup,
      I_SalesOrder.CreationDate          as OrderDate,
      I_SalesOrder.FiscalYear,
      I_SalesOrder.TransactionCurrency   as Currency,

      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'Currency'
      I_SalesOrderItem.NetAmount         as Revenue,

      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'Currency'
      I_SalesOrderItem.CostAmount        as COGS,

      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'Currency'
      I_SalesOrderItem.NetAmount
        - I_SalesOrderItem.CostAmount    as GrossProfit,

      @DefaultAggregation: #SUM
      @Semantics.amount.currencyCode: 'DisplayCurrency'
      currency_conversion(
        amount             => I_SalesOrderItem.NetAmount,
        source_currency    => I_SalesOrder.TransactionCurrency,
        target_currency    => :P_DisplayCurrency,
        exchange_rate_date => :P_RateDate,
        exchange_rate_type => 'M',
        error_handling     => 'SET_TO_NULL'
      )                                  as ConvertedRevenue,

      @Semantics.currencyCode: true
      :P_DisplayCurrency                 as DisplayCurrency,

      @DefaultAggregation: #SUM
      @Aggregation.exceptionAggregationSteps: [{
        exceptionAggregationBehavior: #COUNT_DISTINCT,
        exceptionAggregationElements: ['Customer']
      }]
      cast( 0 as abap.int4 )             as UniqueCustomers,

      @DefaultAggregation: #SUM
      @Aggregation.exceptionAggregationSteps: [{
        exceptionAggregationBehavior: #COUNT_DISTINCT,
        exceptionAggregationElements: ['Material']
      }]
      cast( 0 as abap.int4 )             as UniqueMaterials,

      @DefaultAggregation: #SUM
      @Aggregation.exceptionAggregationSteps: [{
        exceptionAggregationBehavior: #MAX,
        exceptionAggregationElements: ['OrderDate']
      }]
      cast( dats'00000000' as abap.dats ) as LastOrderDate,

      -- Virtual Element 1
      @ObjectModel.virtualElement: true
      @ObjectModel.virtualElementCalculatedBy: 'ABAP:ZCL_FULL_VIRT'
      cast( '' as abap.char(10) )         as RiskGrade,

      -- Virtual Element 2
      @ObjectModel.virtualElement: true
      @ObjectModel.virtualElementCalculatedBy: 'ABAP:ZCL_FULL_VIRT'
      cast( 0 as abap.dec(15,2) )         as ForecastRevenue
}
```

## 18. 복잡한 조직 계층 권한 + 다중 Query 구성

```
-- ✅ DCL — 조직 계층 기반 동적 권한 제어
@MappingRole: true
define role ZI_SalesDeliveryCube_DCL
{
  grant select on ZI_SalesDeliveryCube
    where (SalesOrg) = aspect pfcg_auth(
      V_VBKA_VKO, VKORG, ACTVT = '03'
    )
    and (CompanyCode) = aspect pfcg_auth(
      F_BKPF_BUK, BUKRS, ACTVT = '03'
    );
}

-- ✅ QUERY View 1 — 수주/납품 현황
@AccessControl.authorizationCheck: #NOT_ALLOWED
define transient view entity ZQ_SalesDeliveryQuery1
  provider contract analytical_query
  as projection on ZI_SalesDeliveryCube
{
  @AnalyticsDetails.query.axis: #ROWS
  DocCategory,
  Customer,
  Material,
  SalesOrg,
  FiscalYear,
  DocStatus,
  RiskGrade,

  @AnalyticsDetails.query.axis: #COLUMNS
  Amount,
  ConvertedAmount,
  UniqueCustomers,
  DisplayCurrency,

  -- Formula — 예측 대비 실적률
  @Aggregation.default: #FORMULA
  $projection.Amount / $projection.ForecastAmount * 100
                as AchievementRate,

  -- Restricted — 수주만
  @AnalyticsDetails.query.formula:
    '$restricted( $projection.Amount, DocCategory = ''ORDER'' )'
  cast( 0 as abap.dec(15,2) ) as OrderAmount,

  -- Restricted — 납품만
  @AnalyticsDetails.query.formula:
    '$restricted( $projection.Amount, DocCategory = ''DELIVERY'' )'
  cast( 0 as abap.dec(15,2) ) as DeliveryAmount,

  -- Formula — 납품률
  @Aggregation.default: #FORMULA
  $projection.DeliveryAmount / $projection.OrderAmount * 100
                as DeliveryRate
}

-- ✅ QUERY View 2 — 전년 비교 + 리스크 분석
@AccessControl.authorizationCheck: #NOT_ALLOWED
define transient view entity ZQ_SalesDeliveryQuery2
  provider contract analytical_query
  as projection on ZI_SalesDeliveryCube
{
  @AnalyticsDetails.query.axis: #ROWS
  Customer,
  SalesOrg,
  FiscalYear,
  RiskGrade,

  @AnalyticsDetails.query.axis: #COLUMNS
  Amount,
  UniqueCustomers,
  DisplayCurrency,

  -- Restricted — 전년 매출
  @AnalyticsDetails.query.formula:
    '$restricted( $projection.Amount, FiscalYear = $prevyear )'
  cast( 0 as abap.dec(15,2) ) as PrevYearAmount,

  -- Formula — YoY 증감률
  @Aggregation.default: #FORMULA
  ( $projection.Amount - $projection.PrevYearAmount )
    / $projection.PrevYearAmount * 100
                as YoYGrowthRate,

  -- Restricted — 고위험 고객 매출
  @AnalyticsDetails.query.formula:
    '$restricted( $projection.Amount, RiskGrade = ''HIGH'' )'
  cast( 0 as abap.dec(15,2) ) as HighRiskAmount,

  -- Formula — 고위험 비중
  @Aggregation.default: #FORMULA
  $projection.HighRiskAmount / $projection.Amount * 100
                as HighRiskShareRate
}
```
