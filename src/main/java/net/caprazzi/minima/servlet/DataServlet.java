package net.caprazzi.minima.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.caprazzi.minima.framework.RequestInfo;
import net.caprazzi.minima.model.List;
import net.caprazzi.minima.model.Note;
import net.caprazzi.minima.service.PushService;
import net.caprazzi.slabs.Slabs;
import net.caprazzi.slabs.SlabsDoc;
import net.caprazzi.slabs.SlabsException;
import net.caprazzi.slabs.SlabsOnKeez.SlabsList;
import net.caprazzi.slabs.SlabsOnKeez.SlabsPut;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
public class DataServlet extends HttpServlet {

	private static Logger logger = LoggerFactory.getLogger("MinimaServlet");
	
	private final String webroot;
	private final Slabs db;

	private final PushService pushService;

	public DataServlet(String webroot, Slabs db, PushService pushService) {
		this.webroot = webroot;
		this.db = db;
		this.pushService = pushService;
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		RequestInfo info = RequestInfo.fromRequest(req);
		if (info.isPath(webroot + "/data/stories")) {
			sendBoard(resp);
			return;
		}
		sendError(resp, 404, "not found");
	}
	
	private void sendBoard(final HttpServletResponse resp) {
		db.list(new SlabsList() {

			@Override
			public void entries(Iterable<SlabsDoc> docs) {
				
				final HashMap<String, List> lists = new HashMap<String,List>();
				final ArrayList<Note> notes = new ArrayList<Note>();
				
				for (SlabsDoc doc : docs) {
					if (doc.getTypeName().equals("list")) {
						List list = (List)doc;
						if (!list.isArchived())
							lists.put(list.getId(), list);
					}
					else if (doc.getTypeName().equals("story")) {
						Note note = (Note)doc;
						if (!note.isArchived())
						notes.add(note);
					}
				}
				
				ObjectMapper mapper = new ObjectMapper();
				ObjectNode root = mapper.createObjectNode();
				
				ArrayNode listsNode = mapper.createArrayNode();
				root.put("lists", listsNode);
				for (List list : lists.values()) {
					listsNode.add(list.toJsonNode());
				}
				
				ArrayNode notesNode = mapper.createArrayNode();
				root.put("stories", notesNode);
				for (Note note : notes) {
					if (lists.containsKey(note.getList()))
					notesNode.add(note.toJsonNode());
				}								
			
				try {					
					resp.setContentType("application/json");
					ServletOutputStream out = resp.getOutputStream();
					mapper.writeValue(out, root);
					out.close();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void error(SlabsException ex) {
				sendError(resp, 500, "Internal Serverl Error");
				ex.getCause().printStackTrace();
			}

			@Override
			public void notFound() {
				sendError(resp, 500, "Internal Serverl Error");
			}
			
			@Override
			public void applicationError(SlabsException ex) {
				sendError(resp, 500, "Internal Serverl Error");
				ex.getCause().printStackTrace();
			}
			
		});
	}

	@Override
	protected void doPut(HttpServletRequest req, final HttpServletResponse resp)
			throws ServletException, IOException {
		
		if (req.getAttribute("minima.readonly").equals(true)) {
			sendError(resp, 403, "not authorised");
			return;
		}
		
		RequestInfo info = RequestInfo.fromRequest(req);
		String senderTag = req.getHeader("X-CLIENT-TAG");
		
		if (info.isPath(webroot + "/data/stories/_/_")) {
			String id = info.get(-2);
			int revision = Integer.parseInt(info.get(-1));		
			SlabsDoc note = Note.fromJson(req.getInputStream());
			save(senderTag, id, revision, note, resp);
			return;
		}
		
		if (info.isPath(webroot + "/data/lists/_/_")) {
			String id = info.get(-2);
			int revision = Integer.parseInt(info.get(-1));
			List list = List.fromJson(req.getInputStream());
			save(senderTag, id, revision, list, resp);
			return;
		}
		
		sendError(resp, 404, "not found");
	}
	
	private void save(final String sender, String id, int revision, SlabsDoc doc, final HttpServletResponse resp) {
		doc.setId(id);
		doc.setRevision(revision);
		db.put(doc, new SlabsPut() {

			@Override
			public void ok(SlabsDoc doc) {
				sendDoc(resp, 201, doc);
				pushService.send(sender, doc);
			}

			@Override
			public void collision(String id, int yourRev, int foundRev) {
				logger.warn("Collision while updating item ["+id+"@"+yourRev+"]: " +
						"was expecting revision " + foundRev);
				sendError(resp, 409, "Could not update item ["+id+"@"+yourRev+"]: " +
						"was expecting revision " + foundRev);
			}

			@Override
			public void error(String id, SlabsException e) {
				logger.error("Error while updating story " + id, e);
				sendError(resp, 500, "Internal Server Error");
			}
			
			@Override
			public void applicationError(SlabsException ex) {
				sendError(resp, 500, "Internal Server Error");
			}
			
		});
	}

	private void sendDoc(HttpServletResponse resp, int status, SlabsDoc doc) {
		resp.setStatus(201);
		OutputStream out;
		try {
			out = resp.getOutputStream();
			doc.toJson(out);
			out.close();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	private static void sendError(HttpServletResponse resp, int i, String message) {
		try  {
			resp.setStatus(i);	
			Writer w = resp.getWriter();
			w.write(message);
			w.flush();
		} catch (IOException e) {
			logger.error("Error writing to servlet output", e);
			throw new RuntimeException("Internal Server Error", e);
		}		
	}

}
