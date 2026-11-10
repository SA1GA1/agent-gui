import {describe, expect, it} from 'vitest';
import {normalizeMath} from './mathNormalizer';

describe('math normalization',()=>{
  it('normalizes bracket and parenthesis formulas',()=>{
    expect(normalizeMath(String.raw`Formula \[W(p)=\frac{Y(p)}{X(p)}\] and \(x^2\).`)).toBe('Formula \n\n$$\nW(p)=\\frac{Y(p)}{X(p)}\n$$\n\n and $x^2$.');
  });
  it('preserves code and currency',()=>{
    const source='Cost $20 and $30. Use `\\(x\\)`.\n~~~tex\n\\[x\\]\n~~~';
    expect(normalizeMath(source)).toBe(source.replaceAll("$20", "\\$20").replaceAll("$30", "\\$30"));
  });
  it('keeps existing math and incomplete streaming delimiters',()=>{
    const source=String.raw`$x$ and $$y$$ and \(unfinished`;
    expect(normalizeMath(source)).toBe(source);
  });
});

import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import ReactMarkdown from 'react-markdown';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';

function renderMath(source:string):string {
  return renderToStaticMarkup(createElement(ReactMarkdown,{remarkPlugins:[remarkMath],rehypePlugins:[[rehypeKatex,{trust:false,throwOnError:false,maxExpand:200,maxSize:20}]],children:normalizeMath(source)}));
}
it('renders the transfer function as local display math with decimal commas',()=>{
  const source=String.raw`\[W(p)=\frac{Y(p)}{X(p)}=\frac{1{,}53p^2+16{,}73p+7{,}93}{0{,}43p^4+p^3+1{,}93p^2}\]`;
  const html=renderMath(source);
  expect(html).toContain('katex-display');
  expect(html).toContain('mfrac');
  expect(html).not.toContain('<img');
  expect(html).not.toContain('katex-error');
});
it('renders multiline formulas and keeps code and currency outside math',()=>{
  const html=renderMath('\\[\nx^2\n\\]\n\nCost $20 and $30. `\\(code\\)`');
  expect(html.match(/class="katex"/g)).toHaveLength(1);
  expect(html).toContain('Cost $20 and $30.');
  expect(html).toContain('<code>\\(code\\)</code>');
});
it('keeps malformed formulas visible and disables trusted TeX features',()=>{
  expect(renderMath(String.raw`\[\unknown{x}\]`)).toContain('\\unknown');
  const html=renderMath(String.raw`\[\includegraphics{https://bad.invalid/a}\]`);
  expect(html).not.toContain('<img');
  expect(html).toContain('includegraphics');
});
