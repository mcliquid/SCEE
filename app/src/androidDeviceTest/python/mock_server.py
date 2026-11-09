from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse,parse_qs
from xml.sax.saxutils import escape
import json,pathlib,threading,socket,argparse
records=[]; events=[]
parser=argparse.ArgumentParser(description='Loopback-only mock for NoteRestartInstrumentation')
parser.add_argument('--output-dir',required=True,type=pathlib.Path)
folder=parser.parse_args().output_dir
folder.mkdir(parents=True,exist_ok=True)
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args): pass
 def send(self,body,kind='text/xml',code=200):
  data=body.encode();self.send_response(code);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
 def event(self,action):
  events.append(action);(folder/'restart-mock-events.json').write_text(json.dumps(events,indent=2),encoding='utf-8')
 def note(self,n):
  r=records[n-1]
  return f'<osm><note lat="{r["lat"]}" lon="{r["lon"]}"><id>{n}</id><date_created>2026-11-08 17:00:00 UTC</date_created><status>open</status><comments><comment><date>2026-11-08 17:00:00 UTC</date><action>opened</action><text>{escape(r["text"])}</text><uid>7</uid><user>synthetic</user></comment></comments></note></osm>'
 def do_POST(self):
  u=urlparse(self.path);body=self.rfile.read(int(self.headers.get('Content-Length','0')))
  self.event({'method':'POST','path':u.path})
  if u.path=='/upload.php': self.send(json.dumps({'future_url':'http://127.0.0.1:18765/photo.jpg'}),'application/json')
  elif u.path=='/activate.php': self.send('{}','application/json')
  elif u.path in ['/notes-api/notes/50/comment','/notes-api/notes/51/comment','/notes-api/notes/50/close']:
   q=parse_qs(u.query);n=int(u.path.split('/')[-2])
   records.append({'id':n,'action':u.path.rsplit('/',1)[1],'text':q.get('text',[''])[0],'lat':'1.0','lon':'1.0'})
   (folder/'restart-mock-notes.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
   if len(records)==1:
    self.close_connection=True;self.connection.shutdown(socket.SHUT_RDWR);self.connection.close()
   else:
    self.send(self.note(len(records)).replace(f'<id>{len(records)}</id>',f'<id>{n}</id>'))
  elif u.path=='/notes-api/notes':
   q=parse_qs(u.query);records.append({k:q[k][0] for k in ['lat','lon','text']})
   (folder/'restart-mock-notes.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
   if len(records)==1:
    self.close_connection=True
    self.connection.shutdown(socket.SHUT_RDWR)
    self.connection.close()
   else:self.send(self.note(len(records)))
  else:self.send('unexpected endpoint','text/plain',403)
 def do_GET(self):
  u=urlparse(self.path);self.event({'method':'GET','path':u.path})
  if u.path.startswith('/notes-api/notes/'):
   self.send(self.note(int(u.path.rsplit('/',1)[1])))
  elif u.path=='/__shutdown':
   self.send('stopped','text/plain');threading.Thread(target=self.server.shutdown).start()
  else:self.send('unexpected endpoint','text/plain',403)
server=HTTPServer(('127.0.0.1',18765),Handler)
print('MOCK READY on loopback 18765',flush=True)
server.serve_forever()
