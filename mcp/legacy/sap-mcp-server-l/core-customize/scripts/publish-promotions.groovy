import de.hybris.platform.servicelayer.search.FlexibleSearchQuery

def flexSearch = spring.getBean("flexibleSearchService")
def modelService = spring.getBean("modelService")
def ruleMaintenanceService = spring.getBean("ruleMaintenanceService")

// Get all promotion source rules
def allRulesQuery = new FlexibleSearchQuery("SELECT {pk} FROM {PromotionSourceRule}")
def allRules = flexSearch.search(allRulesQuery).result
allRules.each { modelService.refresh(it) }

// Only publish rules that are not already PUBLISHED
def unpublished = allRules.findAll { it.status.toString() != "PUBLISHED" }
if (unpublished.isEmpty()) {
    println "All ${allRules.size()} rules already PUBLISHED — nothing to do."
} else {
    println "Publishing ${unpublished.size()} of ${allRules.size()} rules..."
    ruleMaintenanceService.compileAndPublishRules(unpublished, "promotions-module", false)
    Thread.sleep(3000)

    // Refresh and verify
    unpublished.each { modelService.refresh(it) }
    def stillUnpublished = unpublished.findAll { it.status.toString() != "PUBLISHED" }
    if (stillUnpublished) {
        println "WARNING: ${stillUnpublished.size()} rules still not published:"
        stillUnpublished.each { println "  ${it.code}: ${it.status}" }
    } else {
        println "All rules published successfully."
    }
}

// Summary
println "\nStatus:"
allRules.each { modelService.refresh(it); println "  ${it.code}: ${it.status}" }

def activeQuery = new FlexibleSearchQuery("SELECT {pk} FROM {DroolsRule} WHERE {active} = true AND {currentVersion} = true")
def activeRules = flexSearch.search(activeQuery).result
println "\nActive Drools rules: ${activeRules.size()}"
activeRules.each { println "  ${it.code}" }
