export function attachmentRoute(mime:string,size:number): 'image'|'text' {
  if (['image/png','image/jpeg','image/webp','image/gif'].includes(mime)) {
    if(size>10*1024*1024) throw new Error('Изображение больше 10 МБ');
    return 'image';
  }
  if(mime && !mime.startsWith('text/') && !['application/json','application/javascript','application/xml','application/x-yaml'].includes(mime)) throw new Error('Поддерживаются текстовые файлы и изображения');
  if(size>128*1024) throw new Error('Текстовый файл больше 128 КБ');
  return 'text';
}
