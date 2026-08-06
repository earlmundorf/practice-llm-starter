// Clear coupon redemptions for the demo customer so per-customer coupon
// limits (maxRedemptionsPerCustomer) reset and coupons can be demonstrated
// again. Scoped to the demo user only — does not touch coupon definitions,
// promotion rules, or other users' redemptions.
//
// Redemptions are recorded at ORDER COMPLETION (not at coupon apply), so
// re-run this after any rehearsal that placed an order with a coupon:
//   ./gradlew groovy -Pfile=scripts/clear-demo-redemptions.groovy -Pcommit=true
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def uid = "john.doe@thinkshop.com"

def query = new FlexibleSearchQuery(
    "SELECT {r.pk} FROM {CouponRedemption AS r JOIN User AS u ON {r.user} = {u.pk}} " +
    "WHERE {u.uid} = ?uid")
query.addQueryParameter("uid", uid)

def redemptions = flexibleSearchService.search(query).result
println "Found ${redemptions.size()} redemption(s) for ${uid}"
redemptions.each { r ->
    println "  removing ${r.coupon.couponId} redeemed ${r.creationtime}"
    modelService.remove(r)
}
println "Done."
