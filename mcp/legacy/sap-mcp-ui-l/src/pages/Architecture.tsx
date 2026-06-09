import { useCallback, useEffect, useRef, useState } from 'react';
import mermaid from 'mermaid';
import { useDarkMode } from '../contexts/DarkModeContext';

interface MermaidDiagramProps {
  chart: string;
  id: string;
}

const MermaidDiagram = ({ chart, id }: MermaidDiagramProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const renderCountRef = useRef(0);
  const { darkMode } = useDarkMode();

  const renderChart = useCallback(async () => {
    if (!containerRef.current) return;

    renderCountRef.current += 1;
    const renderId = `mermaid-${id}-${renderCountRef.current}-${Date.now()}`;

    mermaid.initialize({
      startOnLoad: false,
      theme: 'base',
      themeVariables: darkMode
        ? {
            background: '#1f2937',
            primaryColor: '#1e3a5f',
            primaryTextColor: '#ffffff',
            primaryBorderColor: '#374151',
            secondaryColor: '#374151',
            secondaryTextColor: '#ffffff',
            secondaryBorderColor: '#4b5563',
            tertiaryColor: '#4b5563',
            tertiaryTextColor: '#ffffff',
            lineColor: '#3b82f6',
            textColor: '#e5e7eb',
            mainBkg: '#1f2937',
            nodeBorder: '#4b5563',
            clusterBkg: '#111827',
            clusterBorder: '#4b5563',
            titleColor: '#ffffff',
            edgeLabelBackground: '#1f2937',
            actorBkg: '#1e3a5f',
            actorBorder: '#4b5563',
            actorTextColor: '#ffffff',
            actorLineColor: '#4b5563',
            signalColor: '#3b82f6',
            signalTextColor: '#e5e7eb',
            noteBkgColor: '#374151',
            noteTextColor: '#e5e7eb',
            noteBorderColor: '#4b5563',
            activationBkgColor: '#1e40af',
            activationBorderColor: '#4b5563',
            labelBoxBkgColor: '#1f2937',
            labelBoxBorderColor: '#4b5563',
            labelTextColor: '#e5e7eb',
          }
        : {
            background: '#ffffff',
            primaryColor: '#dbeafe',
            primaryTextColor: '#111827',
            primaryBorderColor: '#e5e7eb',
            secondaryColor: '#f3f4f6',
            secondaryTextColor: '#111827',
            secondaryBorderColor: '#d1d5db',
            tertiaryColor: '#e5e7eb',
            tertiaryTextColor: '#111827',
            lineColor: '#2563eb',
            textColor: '#374151',
            mainBkg: '#ffffff',
            nodeBorder: '#d1d5db',
            clusterBkg: '#f9fafb',
            clusterBorder: '#d1d5db',
            titleColor: '#111827',
            edgeLabelBackground: '#ffffff',
            actorBkg: '#dbeafe',
            actorBorder: '#d1d5db',
            actorTextColor: '#111827',
            actorLineColor: '#d1d5db',
            signalColor: '#2563eb',
            signalTextColor: '#374151',
            noteBkgColor: '#f3f4f6',
            noteTextColor: '#374151',
            noteBorderColor: '#d1d5db',
            activationBkgColor: '#bfdbfe',
            activationBorderColor: '#d1d5db',
            labelBoxBkgColor: '#ffffff',
            labelBoxBorderColor: '#d1d5db',
            labelTextColor: '#374151',
          },
      fontFamily: 'ui-sans-serif, system-ui, sans-serif',
      flowchart: { curve: 'basis', padding: 20 },
    });

    try {
      const { svg } = await mermaid.render(renderId, chart);
      if (containerRef.current) {
        containerRef.current.innerHTML = svg;
      }
    } catch {
      // mermaid.render creates a temp element on failure — clean it up
      const tempEl = document.getElementById(renderId);
      tempEl?.remove();

      if (containerRef.current) {
        containerRef.current.innerHTML =
          '<p class="text-red-500">Failed to render diagram</p>';
      }
    }
  }, [chart, id, darkMode]);

  useEffect(() => {
    renderChart();
  }, [renderChart]);

  return (
    <div
      ref={containerRef}
      className="overflow-x-auto py-4 flex justify-center"
    />
  );
};

const systemContextChart = `
graph TB
  Customer(["<b>Customer</b><br/>Browses products, places orders,<br/>chats with AI assistant"])

  subgraph ThinkShop["<b>ThinkShop Platform</b>"]
    React["<b>React SPA</b><br/>Single-page storefront with<br/>chat, chips, and entity modals<br/><i>React, TypeScript, Tailwind</i>"]
    SAP["<b>SAP Commerce</b><br/>E-commerce backend: catalog,<br/>cart, checkout, orders<br/><i>Java, Spring, OCC REST API</i>"]
    Agent["<b>AI Agent</b><br/>Streaming chat with tool use,<br/>prompt caching, entity refs<br/><i>Custom OCC extension</i>"]
    MCP["<b>MCP Server</b><br/>Exposes commerce tools<br/>to external AI clients<br/><i>JSON-RPC 2.0</i>"]
  end

  Solr["<b>Apache Solr</b><br/>Product search index<br/>and faceted navigation"]
  DB["<b>MySQL</b><br/>Product catalog, users,<br/>orders, carts"]
  LLM["<b>Anthropic Messages API</b><br/>Claude Sonnet 4.6 with<br/>prompt caching + SSE streaming"]
  ExtClient(["<b>External AI Client</b><br/>Claude Code, Cursor, etc."])

  Customer -- "HTTPS" --> React
  React -- "OCC REST API" --> SAP
  React -- "POST /agent/chat[/stream]" --> Agent
  SAP -- "FlexibleSearch" --> DB
  SAP -- "Solr queries" --> Solr
  Agent -- "Cached prefix + tools" --> LLM
  LLM -- "SSE deltas / tool calls" --> Agent
  Agent -- "Commerce facades" --> SAP
  MCP -- "Commerce facades" --> SAP
  ExtClient -- "JSON-RPC tools" --> MCP

  classDef person fill:#2563eb,stroke:#1d4ed8,color:#fff,stroke-width:2px
  classDef system fill:#3b82f6,stroke:#2563eb,color:#fff
  classDef external fill:#6b7280,stroke:#4b5563,color:#fff
  classDef boundary fill:none,stroke:#d1d5db,stroke-width:2px,stroke-dasharray:5 5,color:#6b7280

  class Customer,ExtClient person
  class React,SAP,Agent,MCP system
  class Solr,DB,LLM external
`;

const workflowChart = `
sequenceDiagram
  actor Customer
  participant UI as React SPA
  participant SAP as SAP Commerce<br/>OCC API
  participant AI as AI Agent
  participant LLM as Anthropic<br/>Sonnet 4.6
  participant MCP as MCP Server
  actor ExtClient as External<br/>AI Client

  Note over Customer,LLM: Direct Product Search
  Customer->>UI: Search "cameras"
  UI->>SAP: GET /products/search?query=cameras
  SAP-->>UI: Products + facets
  UI-->>Customer: Search results

  Note over Customer,LLM: AI-Assisted Shopping (streaming)
  Customer->>UI: "Find me a camera under $500"
  UI->>AI: POST /agent/chat/stream
  Note over AI: Falls back to /agent/chat<br/>if SSE is unavailable
  AI->>LLM: Messages + 18 tools (cached prefix)
  LLM-->>AI: tool_use (product_search)
  AI-->>UI: event: tool {name:"product_search"}
  UI-->>Customer: "Searching products…" indicator
  AI->>SAP: Execute tool (commerce facades)
  SAP-->>AI: Results
  AI->>LLM: Tool results
  LLM-->>AI: text deltas (streamed)
  AI-->>UI: event: text "Here…"
  UI-->>Customer: Streamed reply text
  AI-->>UI: event: done {entityRefs, action?}
  UI-->>Customer: Render product chips below message

  Note over Customer,LLM: Chip → Modal (no backend roundtrip)
  Customer->>UI: Click product chip
  UI->>SAP: GET /products/{code}
  SAP-->>UI: Product details
  UI-->>Customer: Product modal opens

  Note over Customer,LLM: MCP Integration
  ExtClient->>MCP: JSON-RPC tool call
  MCP->>SAP: Commerce facades
  SAP-->>MCP: Result
  MCP-->>ExtClient: JSON-RPC response
`;

const techStack = [
  {
    category: 'Frontend',
    items: [
      { name: 'React 19', detail: 'Functional components & hooks' },
      { name: 'TypeScript', detail: 'Strict mode' },
      { name: 'Tailwind CSS 4', detail: 'Utility-first styling' },
      { name: 'Vite 7', detail: 'Dev server with proxy' },
    ],
  },
  {
    category: 'Backend',
    items: [
      { name: 'SAP Commerce 22.11', detail: 'Hybris e-commerce platform' },
      { name: 'Java 17', detail: 'ServiceLayer pattern' },
      { name: 'Spring Framework', detail: 'Dependency injection & MVC' },
      { name: 'OCC REST API', detail: 'Commerce web services' },
    ],
  },
  {
    category: 'Search & Data',
    items: [
      { name: 'Apache Solr', detail: 'Faceted search via solrfacetsearch' },
      { name: 'MySQL', detail: 'Dev + prod database' },
      { name: 'FlexibleSearch', detail: 'Hybris query language' },
      { name: 'ImpEx', detail: 'Data import/export' },
    ],
  },
  {
    category: 'AI & Integration',
    items: [
      { name: 'Anthropic Claude Sonnet 4.6', detail: 'Tool use + prompt caching (5-min ephemeral)' },
      { name: 'Server-Sent Events', detail: 'Streaming chat replies with graceful JSON fallback' },
      { name: 'MCP Protocol', detail: 'JSON-RPC 2.0 tool server for external AI clients' },
      { name: 'OAuth2', detail: 'Resource Owner Password flow' },
      { name: 'Swagger / OpenAPI', detail: 'API documentation' },
    ],
  },
];

export const Architecture = () => {
  const [activeTab, setActiveTab] = useState<'context' | 'workflow' | 'stack'>('context');

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-blue-50 to-slate-100 dark:from-gray-900 dark:via-slate-900 dark:to-gray-900 transition-colors duration-300">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
            System Architecture
          </h1>
          <p className="mt-2 text-gray-600 dark:text-gray-400">
            How ThinkShop connects React, SAP Commerce, Solr, AI agents, and MCP.
          </p>
        </div>

        {/* Tab Navigation */}
        <div className="flex space-x-1 bg-gray-100 dark:bg-gray-800 rounded-lg p-1 mb-8 max-w-md">
          {([
            { key: 'context', label: 'System Context' },
            { key: 'workflow', label: 'Workflow' },
            { key: 'stack', label: 'Tech Stack' },
          ] as const).map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setActiveTab(key)}
              className={`flex-1 px-4 py-2 text-sm font-medium rounded-md transition-all duration-200 ${
                activeTab === key
                  ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-white shadow-sm'
                  : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {/* Content */}
        {activeTab === 'context' && (
          <section className="bg-white dark:bg-gray-800 rounded-2xl shadow-lg border border-gray-200 dark:border-gray-700 p-6">
            <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
              System Context Diagram
            </h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
              High-level view of actors and systems. The React SPA communicates with SAP Commerce
              for product/order data and with the Agent endpoint for AI chat. External MCP clients
              connect via JSON-RPC.
            </p>
            <MermaidDiagram chart={systemContextChart} id="system-context" />
          </section>
        )}

        {activeTab === 'workflow' && (
          <section className="bg-white dark:bg-gray-800 rounded-2xl shadow-lg border border-gray-200 dark:border-gray-700 p-6">
            <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
              Request Workflow
            </h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
              Three interaction patterns: direct product search via OCC, AI-assisted shopping
              through the agent endpoint, and MCP integration for external AI clients.
            </p>
            <MermaidDiagram chart={workflowChart} id="workflow" />
          </section>
        )}

        {activeTab === 'stack' && (
          <section className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {techStack.map((group) => (
              <div
                key={group.category}
                className="bg-white dark:bg-gray-800 rounded-2xl shadow-lg border border-gray-200 dark:border-gray-700 p-6"
              >
                <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                  {group.category}
                </h3>
                <ul className="space-y-3">
                  {group.items.map((item) => (
                    <li key={item.name} className="flex items-start space-x-3">
                      <span className="mt-1 h-2 w-2 rounded-full bg-blue-500 flex-shrink-0" />
                      <div>
                        <span className="font-medium text-gray-900 dark:text-white">
                          {item.name}
                        </span>
                        <span className="text-gray-500 dark:text-gray-400 text-sm ml-2">
                          {item.detail}
                        </span>
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </section>
        )}
      </div>
    </div>
  );
};
