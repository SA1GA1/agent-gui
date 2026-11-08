export function attachmentRoute(mime:string,size:number): 'image'|'text' {
  if (['image/png','image/jpeg','image/webp','image/gif'].includes(mime)) {
    if(size>10*1024*1024) throw new Error('Image exceeds 10 MB');
    return 'image';
  }
  if(mime && !mime.startsWith('text/') && !['application/json','application/javascript','application/xml','application/x-yaml'].includes(mime)) throw new Error('Text files and images are supported');
  if(size>128*1024) throw new Error('Text file exceeds 128 KB');
  return 'text';
}
