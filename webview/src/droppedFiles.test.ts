import {expect,it} from 'vitest';
import {attachmentRoute} from './droppedFiles';
it('routes known text and images while allowing arbitrary files',()=>{
 expect(attachmentRoute('text/plain',200)).toBe('text');
 expect(attachmentRoute('image/png',200)).toBe('image');
 expect(attachmentRoute('application/pdf',200)).toBe('binary');
 expect(attachmentRoute('application/vnd.openxmlformats-officedocument.wordprocessingml.document',200)).toBe('binary');
 expect(attachmentRoute('',200)).toBe('binary');
 expect(attachmentRoute('image/svg+xml',200)).toBe('text');
 expect(attachmentRoute('text/plain',131073)).toBe('text');
});
it('enforces the same ten MB limit for all formats',()=>{
 for(const mime of ['text/plain','image/png','application/pdf','']) {
  expect(()=>attachmentRoute(mime,10*1024*1024)).not.toThrow();
  expect(()=>attachmentRoute(mime,10*1024*1024+1)).toThrow('10 MB');
 }
});
