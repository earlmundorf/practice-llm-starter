#!/bin/bash
# index-solr.sh — Run full Solr indexing for all configured search indexes.
# Required after "ant initialize" or whenever search results are empty.
# Requires the server to be running.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== INDEXING SOLR ==="
"$SCRIPT_DIR/hac-groovy.sh" 'def indexerService = spring.getBean("indexerService")
def facetSearchConfigService = spring.getBean("facetSearchConfigService")
["Solr Config for Backoffice", "Solr Config for Backoffice Visibility Product", "thinkshopIndex"].each { name ->
    try {
        def config = facetSearchConfigService.getConfiguration(name)
        indexerService.performFullIndex(config)
        println "OK: ${name}"
    } catch (e) {
        println "FAIL: ${name} - ${e.message}"
    }
}' --commit
RESULT=$?

echo "=== DONE ==="
exit $RESULT
