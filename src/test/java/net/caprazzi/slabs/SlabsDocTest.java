package net.caprazzi.slabs;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;

public class SlabsDocTest {

	private TestDoc doc;

	@Before 
	public void set_up() {
		doc = new TestDoc();
		doc.setValue("some value");
		doc.setId("the id");
		doc.setRevision(99);
	}
	
	@Test
	public void should_discover_the_type_name() {
		TestDoc doc = new TestDoc();
		assertEquals("test_doc", doc.getTypeName());
	}
	
	@Test
	public void should_serialize_to_db_format() throws JsonParseException, IOException, SlabsException {
		ObjectNode root = doc.getDatabaseJson();
		assertEquals(doc.getTypeName(), root.get("name").getValueAsText());		
		ObjectNode node = (ObjectNode) root.get("obj");
		assertEquals(null, node.get("id"));
		assertEquals(null, node.get("revision"));
		assertEquals(doc.getValue(), node.get("value").getValueAsText());		
	}
	
	@Test
	public void should_serialize_to_plain_format_with_idrev() {
		ObjectNode node = doc.toInternalJson();
		assertEquals(null, node.get("id"));
		assertEquals(null, node.get("revision"));
		assertEquals(doc.getValue(), node.get("value").getValueAsText());
	}
	
	@Test
	public void should_serialize_to_plain_format_without_idrev() {
		ObjectNode node = doc.toInternalJson();
		assertNull(node.get("id"));
		assertNull(node.get("revision"));
		assertEquals(doc.getValue(), node.get("value").getValueAsText());
	}
}
