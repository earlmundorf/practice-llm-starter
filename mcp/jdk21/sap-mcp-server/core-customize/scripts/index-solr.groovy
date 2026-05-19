def indexerService = spring.getBean("indexerService")
def facetSearchConfigService = spring.getBean("facetSearchConfigService")
["Solr Config for Backoffice", "Solr Config for Backoffice Visibility Product", "thinkshopIndex"].each { name ->
    try {
        def config = facetSearchConfigService.getConfiguration(name)
        indexerService.performFullIndex(config)
        println "OK: ${name}"
    } catch (e) {
        println "FAIL: ${name} - ${e.message}"
    }
}
