package net.caprazzi.minima.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.caprazzi.minima.framework.RequestInfo;
import net.caprazzi.minima.model.Meta;
import net.caprazzi.minima.model.Story;
import net.caprazzi.minima.model.StoryList;
import net.caprazzi.minima.service.MinimaService;
import net.caprazzi.minima.service.MinimaService.CreateStory;
import net.caprazzi.minima.service.MinimaService.Update;

import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.util.IO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

@SuppressWarnings("serial")
public class MinimaServlet extends HttpServlet {

	private static Logger logger = LoggerFactory.getLogger("MinimaServlet");
	
	private final MinimaService minimaService;

	private final String webroot;

	@Inject
	public MinimaServlet(String webroot, MinimaService minimaService) {
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
		
		resp.setContentType("application/json");
		Writer out = resp.getWriter();
		minimaService.writeBoard(out);
		out.close();
		return;
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
			saveStory(req, resp, info);
			return;
		}
		
		if (info.isPath(webroot + "/data/lists/_/_")) {
			saveList(req, resp, info);
			return;
		}
		
		sendError(resp, 404, "not found");
	}

	private void saveList(HttpServletRequest req, final HttpServletResponse resp,
			RequestInfo info) throws IOException {
		
		String key = info.get(-2);
		int revision = Integer.parseInt(info.get(-1));
		byte[] data = IO.readBytes(req.getInputStream());
		
		StoryList list = StoryList.fromJson(data);
		Meta<StoryList> wrap = Meta.wrap("list", list);
		
		minimaService.update(key, revision, wrap.toJson(), new Update() {

			@Override
			public void error(String message, Exception e) {
				logger.error("Error while updating story " + message, e);
				sendError(resp, 500, "Internal Server Error");
			}

			@Override
			public void success(String key, int revision, byte[] jsonData) {
				Meta<StoryList> meta = Meta.fromJson(StoryList.class, jsonData);
				sendJson(resp, 201, meta.getObj().toJson());
			}

			@Override
			public void collision(String key, int yourRev, int foundRev) {
				logger.warn("Collision while updating item ["+key+"@"+yourRev+"]: was expecting revision " + foundRev);
				sendError(resp, 409, "Could not update item ["+key+"@"+yourRev+"]: was expecting revision " + foundRev);
			}
		
		});
		
	}

	private void saveStory(HttpServletRequest req,
			final HttpServletResponse resp, RequestInfo info)
			throws IOException {
		String key = info.get(-2);
		int revision = Integer.parseInt(info.get(-1));
		byte[] story = IO.readBytes(req.getInputStream());
		minimaService.update(key, revision, story, new Update() {

			@Override
			public void success(String key, int rev, byte[] jsonData) {
				sendJson(resp, 201, jsonData);
			}
			
			@Override
			public void collision(String key, int yourRev, int foundRev) {
				logger.warn("Collision while updating item ["+key+"@"+yourRev+"]: was expecting revision " + foundRev);
				sendError(resp, 409, "Could not update item ["+key+"@"+yourRev+"]: was expecting revision " + foundRev);
			}
			
			@Override
			public void error(String message, Exception e) {
				logger.error("Error while updating story " + message, e);
				sendError(resp, 500, "Internal Server Error");
			}
			
		});
	}
	
	@Override
	protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
			throws ServletException, IOException {
		
		if (req.getAttribute("minima.readonly").equals(true)) {
			sendError(resp, 403, "not authorised");
			return;
		}
		
		RequestInfo info = RequestInfo.fromRequest(req);
		if (!info.isPath(webroot + "/data/stories/_")) {
			sendError(resp, 404, "not found");
			return;
		}
		
		String key = info.get(-1);
		
		minimaService.createStory(key, IO.readBytes(req.getInputStream()), new CreateStory() {
			@Override public void success(Story saved) {
				sendStoryJson(resp, 201, saved);
			}
			@Override public void error(String string, Exception e) {
				logger.warn(string, e);
				try {
					resp.sendError(400);
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			}
		});
	}
	
	protected void sendStoryJson(HttpServletResponse resp, int status, Story story) {
		try {
			resp.setContentType("application/json");
			resp.setStatus(201);
			PrintWriter out = resp.getWriter();
			 ObjectMapper mapper = new ObjectMapper();
			 try {
				mapper.writeValue(out, story);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			out.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	protected void sendJson(HttpServletResponse resp, int status, byte[] data) {
		try {
			resp.setContentType("application/json");
			resp.setStatus(201);
			ServletOutputStream out = resp.getOutputStream();
			out.write(data);
			out.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	protected void sendError(HttpServletResponse resp, int i, String message) {
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
