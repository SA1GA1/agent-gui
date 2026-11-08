import {expect,it} from 'vitest';
import {attachmentRoute} from './droppedFiles';
it('accepts text drops instead of silently skipping non-images',()=>{
 expect(attachmentRoute('text/plain',200)).toBe('text');
 expect(attachmentRoute('image/png',200)).toBe('image');
 expect(()=>attachmentRoute('application/pdf',200)).toThrow();
 expect(()=>attachmentRoute('text/plain',131073)).toThrow();
});
