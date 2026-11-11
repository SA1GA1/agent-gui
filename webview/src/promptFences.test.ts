import {expect,it} from 'vitest';
import {fenceEnter,fenceRanges} from './promptFences';
it('creates editable fenced blocks and avoids submitting their newlines',()=>{
 expect(fenceEnter('```kotlin',9)).toBe('\n\n```');
 expect(fenceEnter('```\nhello\n```',9)).toBe('\n');
 expect(fenceEnter('```\nhello',9)).toBe('\n');
 expect(fenceEnter('```\nhello\n```',13)).toBeUndefined();
 expect(fenceEnter('```ts\nx\n```',5)).toBe('\n');
 expect(fenceRanges('before\n```\ncode\n```\nafter')).toHaveLength(1);
});
