import de.hybris.platform.util.Config

def mask = { v -> v == null ? "(null)" : v.length() <= 12 ? v : "${v.take(8)}... (${v.length()} chars)" }

println "=== Non-secret config (Config.getString from local.properties) ==="
['coremcp.llm.provider', 'coremcp.llm.timeout.seconds',
 'coremcp.openai.model', 'coremcp.openai.intent.model', 'coremcp.openai.baseurl',
 'coremcp.anthropic.model', 'coremcp.anthropic.intent.model', 'coremcp.anthropic.version', 'coremcp.anthropic.baseurl',
 'coremcp.openai-compatible.baseurl', 'coremcp.openai-compatible.model',
 'coremcp.openai-compatible.intent.model', 'coremcp.openai-compatible.completions.path'].each { k ->
    println "  ${k.padRight(48)} = '${Config.getParameter(k)}'"
}
println ""
println "=== Secrets (System.getenv) ==="
println "  OPENAI_API_KEY              = ${mask(System.getenv('OPENAI_API_KEY'))}"
println "  ANTHROPIC_API_KEY           = ${mask(System.getenv('ANTHROPIC_API_KEY'))}"
println "  OPENAI_COMPATIBLE_API_KEY   = ${mask(System.getenv('OPENAI_COMPATIBLE_API_KEY'))}"
println ""
println "Result: secret api keys live in env only, everything else in local.properties."
