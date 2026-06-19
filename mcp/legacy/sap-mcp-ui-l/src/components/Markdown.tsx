import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeExternalLinks from 'rehype-external-links';

interface MarkdownProps {
  children: string;
  className?: string;
}

export const Markdown = ({ children, className }: MarkdownProps) => {
  const markdown = (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      rehypePlugins={[[rehypeExternalLinks, { target: '_blank', rel: ['noopener', 'noreferrer'] }]]}
      components={{
        a: (props) => (
          <a
            {...props}
            className="text-blue-600 dark:text-blue-400 underline underline-offset-2 hover:text-blue-700 dark:hover:text-blue-300"
          />
        ),
        p: (props) => <p {...props} className="mb-2 last:mb-0" />,
        ul: (props) => <ul {...props} className="list-disc pl-5 mb-2 last:mb-0" />,
        ol: (props) => <ol {...props} className="list-decimal pl-5 mb-2 last:mb-0" />,
        table: (props) => (
          <table {...props} className="border-collapse my-2 text-sm" />
        ),
        th: (props) => (
          <th {...props} className="border border-gray-300 dark:border-gray-600 px-2 py-1 text-left font-semibold" />
        ),
        td: (props) => (
          <td {...props} className="border border-gray-300 dark:border-gray-600 px-2 py-1" />
        ),
        code: (props) => (
          <code {...props} className="bg-gray-200 dark:bg-gray-800 rounded px-1 py-0.5 text-sm" />
        ),
      }}
    >
      {children}
    </ReactMarkdown>
  );

  if (className) {
    return <div className={className}>{markdown}</div>;
  }
  return markdown;
};
