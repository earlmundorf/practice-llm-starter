// =============================================================================
// ThinkShop Sample Promotions
//
// Creates 5 promotion rules and 2 coupons via the model-service. The BOGO rule
// uses the y_container + y_partner_order_entry_percentage_discount pattern with
// maxAllowedRuns=0 so the engine fires it once per pair of mice in the cart.
// Idempotent — safe to re-run.
// =============================================================================

import de.hybris.platform.servicelayer.search.FlexibleSearchQuery
import de.hybris.platform.promotionengineservices.model.PromotionSourceRuleModel
import de.hybris.platform.couponservices.model.SingleCodeCouponModel

def flexSearch = spring.getBean("flexibleSearchService")
def modelService = spring.getBean("modelService")

def getOrCreateRule = { code ->
    def query = new FlexibleSearchQuery("SELECT {pk} FROM {PromotionSourceRule} WHERE {code} = ?code")
    query.addQueryParameter("code", code)
    def results = flexSearch.search(query).result
    if (results) { println "  (updating ${code})"; return results[0] }
    def model = modelService.create(PromotionSourceRuleModel.class)
    model.setCode(code)
    return model
}

def getOrCreateCoupon = { couponId ->
    def query = new FlexibleSearchQuery("SELECT {pk} FROM {SingleCodeCoupon} WHERE {couponId} = ?id")
    query.addQueryParameter("id", couponId)
    def results = flexSearch.search(query).result
    if (results) { println "  (updating ${couponId})"; return results[0] }
    def model = modelService.create(SingleCodeCouponModel.class)
    model.setCouponId(couponId)
    return model
}

def promoGroup = flexSearch.search(
    new FlexibleSearchQuery("SELECT {pk} FROM {PromotionGroup} WHERE {Identifier} = 'thinkshopPromoGrp'")
).result[0]

def now = new Date()
def cal = Calendar.getInstance(); cal.time = now; cal.add(Calendar.DAY_OF_YEAR, 90)
def endDate = cal.time

println "Date range: ${now} to ${endDate}\n"

// 1. Free Shipping on orders >= $1,000
def rule1 = getOrCreateRule("free_shipping_1000")
rule1.setName("Free Shipping on orders over 1000")
rule1.setPriority((Integer) 100)
rule1.setStartDate(now); rule1.setEndDate(endDate)
rule1.setWebsite(promoGroup)
rule1.setConditions('[{"definitionId":"y_cart_total","parameters":{"value":{"uuid":"a1","type":"Map(ItemType(Currency),java.math.BigDecimal)","value":{"USD":1000}},"operator":{"uuid":"a2","type":"Enum(de.hybris.platform.ruledefinitions.AmountOperator)","value":"GREATER_THAN_OR_EQUAL"}},"children":[]}]')
rule1.setActions('[{"definitionId":"y_change_delivery_mode","parameters":{"delivery_mode":{"uuid":"a3","type":"ItemType(DeliveryMode)","value":"thinkshop-free-delivery"}}}]')
rule1.setMessageFired("Free shipping on orders over \$1,000")
modelService.save(rule1)
println "1. free_shipping_1000 — OK"

// 2. LAPTOP10 coupon + 10% off Laptop Pro 15
def coupon1 = getOrCreateCoupon("LAPTOP10")
coupon1.setName("10% off Laptop"); coupon1.setActive(Boolean.TRUE)
coupon1.setMaxRedemptionsPerCustomer((Integer) 1); coupon1.setMaxTotalRedemptions((Integer) 30)
coupon1.setStartDate(now); coupon1.setEndDate(endDate)
modelService.save(coupon1)
println "   LAPTOP10 coupon — OK"

def rule2 = getOrCreateRule("laptop_10pct_coupon")
rule2.setName("10% Off Laptop with Coupon")
rule2.setPriority((Integer) 50)
rule2.setStartDate(now); rule2.setEndDate(endDate)
rule2.setWebsite(promoGroup)
rule2.setConditions('[{"definitionId":"y_qualifying_coupons","parameters":{"coupons":{"uuid":"b1","type":"List(ItemType(AbstractCoupon))","value":["LAPTOP10"]}},"children":[]},{"definitionId":"y_qualifying_products","parameters":{"products_operator":{"uuid":"b2","type":"Enum(de.hybris.platform.ruledefinitions.CollectionOperator)","value":"CONTAINS_ANY"},"quantity":{"uuid":"b3","type":"java.lang.Integer","value":1},"catalog":{"uuid":"b4","type":"ItemType(Catalog)"},"operator":{"uuid":"b5","type":"Enum(de.hybris.platform.ruledefinitions.AmountOperator)","value":"GREATER_THAN_OR_EQUAL"},"products":{"uuid":"b6","type":"List(ItemType(Product))","value":["LAPTOP_PRO_15::electronicsProductCatalog"]}},"children":[]}]')
rule2.setActions('[{"definitionId":"y_order_entry_percentage_discount","parameters":{"value":{"uuid":"b7","type":"java.math.BigDecimal","value":10}}}]')
rule2.setMessageFired("10% off Laptop Pro 15 with coupon LAPTOP10")
modelService.save(rule2)
println "2. laptop_10pct_coupon — OK"

// 3. True BOGO on Wireless Gaming Mouse — partner-action with maxAllowedRuns=0
def rule3 = getOrCreateRule("bogo_mouse")
rule3.setName("Buy One Get One Free — Wireless Gaming Mouse")
rule3.setPriority((Integer) 75)
rule3.setMaxAllowedRuns((Integer) 0)
rule3.setStartDate(now); rule3.setEndDate(endDate)
rule3.setWebsite(promoGroup)
rule3.setConditions('[{"definitionId":"y_container","parameters":{"id":{"uuid":"cx","type":"java.lang.String","value":"CONTAINER_X"}},"children":[{"definitionId":"y_qualifying_products","parameters":{"products_operator":{"uuid":"cx1","type":"Enum(de.hybris.platform.ruledefinitions.CollectionOperator)","value":"CONTAINS_ANY"},"quantity":{"uuid":"cx2","type":"java.lang.Integer","value":1},"catalog":{"uuid":"cx3","type":"ItemType(Catalog)"},"operator":{"uuid":"cx4","type":"Enum(de.hybris.platform.ruledefinitions.AmountOperator)","value":"GREATER_THAN_OR_EQUAL"},"products":{"uuid":"cx5","type":"List(ItemType(Product))","value":["WIRELESS_GAMING_MOUSE::electronicsProductCatalog"]}},"children":[]}]},{"definitionId":"y_container","parameters":{"id":{"uuid":"cy","type":"java.lang.String","value":"CONTAINER_Y"}},"children":[{"definitionId":"y_qualifying_products","parameters":{"products_operator":{"uuid":"cy1","type":"Enum(de.hybris.platform.ruledefinitions.CollectionOperator)","value":"CONTAINS_ANY"},"quantity":{"uuid":"cy2","type":"java.lang.Integer","value":1},"catalog":{"uuid":"cy3","type":"ItemType(Catalog)"},"operator":{"uuid":"cy4","type":"Enum(de.hybris.platform.ruledefinitions.AmountOperator)","value":"GREATER_THAN_OR_EQUAL"},"products":{"uuid":"cy5","type":"List(ItemType(Product))","value":["WIRELESS_GAMING_MOUSE::electronicsProductCatalog"]}},"children":[]}]}]')
rule3.setActions('[{"definitionId":"y_partner_order_entry_percentage_discount","parameters":{"selection_strategy":{"uuid":"c6","type":"Enum(de.hybris.platform.ruleengineservices.enums.OrderEntrySelectionStrategy)","value":"CHEAPEST"},"qualifying_containers":{"uuid":"c7","type":"Map(java.lang.String,java.lang.Integer)","value":{"CONTAINER_X":1}},"target_containers":{"uuid":"c8","type":"Map(java.lang.String,java.lang.Integer)","value":{"CONTAINER_Y":1}},"value":{"uuid":"c9","type":"java.math.BigDecimal","value":100}}}]')
rule3.setMessageFired("Buy one get one free on Wireless Gaming Mouse")
modelService.save(rule3)
println "3. bogo_mouse — OK"

// 4. 10% off Wireless Headphones (auto)
def rule4 = getOrCreateRule("headphones_10pct")
rule4.setName("10% Off Wireless Headphones")
rule4.setPriority((Integer) 60)
rule4.setStartDate(now); rule4.setEndDate(endDate)
rule4.setWebsite(promoGroup)
rule4.setConditions('[{"definitionId":"y_qualifying_products","parameters":{"products_operator":{"uuid":"d1","type":"Enum(de.hybris.platform.ruledefinitions.CollectionOperator)","value":"CONTAINS_ANY"},"quantity":{"uuid":"d2","type":"java.lang.Integer","value":1},"catalog":{"uuid":"d3","type":"ItemType(Catalog)"},"operator":{"uuid":"d4","type":"Enum(de.hybris.platform.ruledefinitions.AmountOperator)","value":"GREATER_THAN_OR_EQUAL"},"products":{"uuid":"d5","type":"List(ItemType(Product))","value":["WIRELESS_HEADPHONES::electronicsProductCatalog"]}},"children":[]}]')
rule4.setActions('[{"definitionId":"y_order_entry_percentage_discount","parameters":{"value":{"uuid":"d6","type":"java.math.BigDecimal","value":10}}}]')
rule4.setMessageFired("10% off Wireless Headphones")
modelService.save(rule4)
println "4. headphones_10pct — OK"

// 5. SPEAKER5 coupon + 5% off Bluetooth Speaker (30-day window)
def coupon2 = getOrCreateCoupon("SPEAKER5")
coupon2.setName("5% off Bluetooth Speaker"); coupon2.setActive(Boolean.TRUE)
coupon2.setMaxRedemptionsPerCustomer((Integer) 1); coupon2.setMaxTotalRedemptions((Integer) 50)
coupon2.setStartDate(now)
def cal30 = Calendar.getInstance(); cal30.time = now; cal30.add(Calendar.DAY_OF_YEAR, 30)
coupon2.setEndDate(cal30.time)
modelService.save(coupon2)
println "   SPEAKER5 coupon — OK"

def rule5 = getOrCreateRule("speaker_5pct_coupon")
rule5.setName("5% Off Bluetooth Speaker with Coupon")
rule5.setPriority((Integer) 40)
rule5.setStartDate(now); rule5.setEndDate(cal30.time)
rule5.setWebsite(promoGroup)
rule5.setConditions('[{"definitionId":"y_qualifying_coupons","parameters":{"coupons":{"uuid":"e1","type":"List(ItemType(AbstractCoupon))","value":["SPEAKER5"]}},"children":[]},{"definitionId":"y_qualifying_products","parameters":{"products_operator":{"uuid":"e2","type":"Enum(de.hybris.platform.ruledefinitions.CollectionOperator)","value":"CONTAINS_ANY"},"quantity":{"uuid":"e3","type":"java.lang.Integer","value":1},"catalog":{"uuid":"e4","type":"ItemType(Catalog)"},"operator":{"uuid":"e5","type":"Enum(de.hybris.platform.ruledefinitions.AmountOperator)","value":"GREATER_THAN_OR_EQUAL"},"products":{"uuid":"e6","type":"List(ItemType(Product))","value":["BLUETOOTH_SPEAKER::electronicsProductCatalog"]}},"children":[]}]')
rule5.setActions('[{"definitionId":"y_order_entry_percentage_discount","parameters":{"value":{"uuid":"e7","type":"java.math.BigDecimal","value":5}}}]')
rule5.setMessageFired("5% off Bluetooth Speaker with coupon SPEAKER5")
modelService.save(rule5)
println "5. speaker_5pct_coupon — OK"

println "\n=== DONE ==="
