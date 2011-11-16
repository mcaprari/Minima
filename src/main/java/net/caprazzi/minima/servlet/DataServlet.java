package net.caprazzi.minima.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.caprazzi.minima.framework.RequestInfo;
import net.caprazzi.minima.model.List;
import net.caprazzi.minima.model.Note;
import net.caprazzi.minima.service.DataService;
import net.caprazzi.slabs.Slabs;
import net.caprazzi.slabs.SlabsDoc;
import net.caprazzi.slabs.SlabsException;
import net.caprazzi.slabs.SlabsOnKeez;
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
	
	private final DataService minimaService;

	private final String webroot;

	public DataServlet(String webroot, DataService minimaService) {
		this.webroot = webroot;
		this.minimaService = minimaService;
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		RequestInfo info = RequestInfo.fromRequest(req);
		if (!info.isPath(webroot + "/data/stories")) {
			sendError(resp, 404, "not found");
			return;
		}
		
		sendBoard(resp);
		resp.setContentType("application/json");
		Writer out = resp.getWriter();
		minimaService.writeJsonBoard(out);
		out.close();
		return;
	}
	
	private void sendBoard(final HttpServletResponse resp) {
		Slabs slabs = new SlabsOnKeez(minimaService.db, new Class[] { Note.class, List.class });
		slabs.list(new SlabsList() {

			@Override
			public void entries(Iterable<SlabsDoc> docs) {
				
				ObjectMapper mapper = new ObjectMapper();
				ObjectNode root = mapper.createObjectNode();
				
				ArrayNode stories = mapper.createArrayNode();
				root.put("stories", stories);
				
				ArrayNode lists = mapper.createArrayNode();
				root.put("lists", lists);
				
				for(SlabsDoc doc : docs) {
					ObjectNode node = doc.toJsonNode();
					if (doc.getTypeName().equals("list")) {
						lists.add(node);
					}
					else if (doc.getTypeName().equals("story")) {						
						stories.add(node);
					}
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
				// TODO Auto-generated method stub
			}

			@Override
			public void notFound() {
				// TODO Auto-generated method stub
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
		
		if (info.isPath(webroot + "/data/stories/_/_")) {
			String id = info.get(-2);
			int revision = Integer.parseInt(info.get(-1));		
			SlabsDoc note = Note.fromJson(req.getInputStream());
			save(id, revision, note, resp);
			return;
		}
		
		if (info.isPath(webroot + "/data/lists/_/_")) {
			String id = info.get(-2);
			int revision = Integer.parseInt(info.get(-1));
			List list = List.fromJson(req.getInputStream());
			save(id, revision, list, resp);
			return;
		}
		
		sendError(resp, 404, "not found");
	}
	
	private void save(String id, int revision, SlabsDoc doc, final HttpServletResponse resp) {
		doc.setId(id);
		doc.setRevision(revision);
		Slabs slabs = new SlabsOnKeez(minimaService.db, new Class[] { Note.class, List.class });
		slabs.put(doc, new SlabsPut() {

			@Override
			public void ok(SlabsDoc doc) {
				sendDoc(resp, 201, doc);
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
