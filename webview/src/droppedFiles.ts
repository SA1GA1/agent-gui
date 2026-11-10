export const MAX_ATTACHMENTS = 10;
export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

export function attachmentRoute(mime:string,size:number): 'image'|'text'|'binary' {
  if(size > MAX_ATTACHMENT_BYTES) throw new Error('File exceeds 10 MB');
  const type = mime.split(';',1)[0].trim().toLowerCase();
  if (['image/png','image/jpeg','image/webp','image/gif'].includes(type)) return 'image';
  if(type.startsWith('text/') || ['application/json','application/javascript','application/xml','application/x-yaml','image/svg+xml'].includes(type)) return 'text';
  // Missing MIME is common for source files. Preserve their bytes for backend detection.
  return 'binary';
}
