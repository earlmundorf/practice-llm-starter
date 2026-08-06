// =============================================================================
// UCP demo discount code: 10OFF — 10% off the entire cart.
//
// This platform's `voucherFacade` alias is backed by the COUPON framework
// (couponfacades aliases defaultCouponFacade as voucherFacade), so a
// redeemable code is a SingleCodeCoupon bound to a promotion source rule —
// the same pattern as sampledatamcp's setup-promotions.groovy. Generous
// redemption limits keep the e2e harness repeatable. Idempotent.
//
// Run (server up), then publish the rule to Drools:
//   ./gradlew groovy -Pfile=hybris/bin/custom/ucpcommerce/resources/ucpcommerce/demo/setup-ucp-demo-coupon.groovy -Pcommit=true
//   ./gradlew groovy -Pfile=scripts/publish-promotions.groovy -Pcommit=true
// =============================================================================

import de.hybris.platform.servicelayer.search.FlexibleSearchQuery
import de.hybris.platform.promotionengineservices.model.PromotionSourceRuleModel
import de.hybris.platform.couponservices.model.SingleCodeCouponModel

def flexSearch = spring.getBean("flexibleSearchService")
def modelService = spring.getBean("modelService")

def couponQuery = new FlexibleSearchQuery("SELECT {pk} FROM {SingleCodeCoupon} WHERE {couponId} = ?id")
couponQuery.addQueryParameter("id", "10OFF")
def couponResults = flexSearch.search(couponQuery).result
def coupon = couponResults ? couponResults[0] : modelService.create(SingleCodeCouponModel.class)

def now = new Date()
def cal = Calendar.getInstance(); cal.time = now; cal.add(Calendar.DAY_OF_YEAR, 365)

coupon.setCouponId("10OFF")
coupon.setName("10% off your order")
coupon.setActive(Boolean.TRUE)
coupon.setMaxRedemptionsPerCustomer((Integer) 999999)
coupon.setMaxTotalRedemptions((Integer) 999999)
coupon.setStartDate(now); coupon.setEndDate(cal.time)
modelService.save(coupon)
println "10OFF coupon — OK"

def ruleQuery = new FlexibleSearchQuery("SELECT {pk} FROM {PromotionSourceRule} WHERE {code} = ?code")
ruleQuery.addQueryParameter("code", "ucp_10off_coupon")
def ruleResults = flexSearch.search(ruleQuery).result
def rule = ruleResults ? ruleResults[0] : modelService.create(PromotionSourceRuleModel.class)

def promoGroup = flexSearch.search(
    new FlexibleSearchQuery("SELECT {pk} FROM {PromotionGroup} WHERE {Identifier} = 'thinkshopPromoGrp'")
).result[0]

rule.setCode("ucp_10off_coupon")
rule.setName("10% Off Order with Coupon 10OFF")
rule.setPriority((Integer) 40)
rule.setStartDate(now); rule.setEndDate(cal.time)
rule.setWebsite(promoGroup)
rule.setConditions('[{"definitionId":"y_qualifying_coupons","parameters":{"coupons":{"uuid":"u1","type":"List(ItemType(AbstractCoupon))","value":["10OFF"]}},"children":[]}]')
rule.setActions('[{"definitionId":"y_order_percentage_discount","parameters":{"value":{"uuid":"u2","type":"java.math.BigDecimal","value":10}}}]')
rule.setMessageFired("10% off your order with coupon 10OFF")
modelService.save(rule)
println "ucp_10off_coupon rule — OK (publish with scripts/publish-promotions.groovy)"
