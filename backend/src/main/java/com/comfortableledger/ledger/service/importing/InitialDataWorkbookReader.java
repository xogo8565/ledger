package com.comfortableledger.ledger.service.importing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.core.io.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

final class InitialDataWorkbookReader {
    private InitialDataWorkbookReader() {
    }

    static List<Map<String, String>> readRows(Resource resource) throws IOException {
        Map<String, byte[]> entries = readZipEntries(resource);
        List<String> sharedStrings = readSharedStrings(entries.get("xl/sharedStrings.xml"));
        byte[] sheet = entries.get("xl/worksheets/sheet1.xml");
        if (sheet == null) {
            throw new IOException("sheet1.xml not found in " + resource.getFilename());
        }
        return readSheetRows(sheet, sharedStrings);
    }

    private static Map<String, byte[]> readZipEntries(Resource resource) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (InputStream inputStream = resource.getInputStream();
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                zipInputStream.transferTo(output);
                entries.put(entry.getName(), output.toByteArray());
            }
        }
        return entries;
    }

    private static List<String> readSharedStrings(byte[] xml) throws IOException {
        List<String> sharedStrings = new ArrayList<>();
        if (xml == null) {
            return sharedStrings;
        }
        Document document = parse(xml);
        NodeList stringItems = document.getElementsByTagName("si");
        for (int i = 0; i < stringItems.getLength(); i++) {
            Element item = (Element) stringItems.item(i);
            NodeList textNodes = item.getElementsByTagName("t");
            StringBuilder value = new StringBuilder();
            for (int j = 0; j < textNodes.getLength(); j++) {
                value.append(textNodes.item(j).getTextContent());
            }
            sharedStrings.add(value.toString());
        }
        return sharedStrings;
    }

    private static List<Map<String, String>> readSheetRows(byte[] xml, List<String> sharedStrings) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        Document document = parse(xml);
        NodeList rowNodes = document.getElementsByTagName("row");
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element rowElement = (Element) rowNodes.item(i);
            NodeList cellNodes = rowElement.getElementsByTagName("c");
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < cellNodes.getLength(); j++) {
                Element cell = (Element) cellNodes.item(j);
                String column = columnName(cell.getAttribute("r"));
                String value = cellValue(cell, sharedStrings);
                if (!column.isBlank() && value != null && !value.isBlank()) {
                    row.put(column, value.trim());
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static String cellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList inlineTextNodes = cell.getElementsByTagName("t");
            return inlineTextNodes.getLength() == 0 ? "" : inlineTextNodes.item(0).getTextContent();
        }

        NodeList valueNodes = cell.getElementsByTagName("v");
        if (valueNodes.getLength() == 0) {
            return "";
        }

        String rawValue = valueNodes.item(0).getTextContent();
        if ("s".equals(type)) {
            int index = Integer.parseInt(rawValue);
            return index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : "";
        }
        return rawValue;
    }

    private static String columnName(String cellReference) {
        return cellReference == null ? "" : cellReference.replaceAll("\\d", "");
    }

    private static Document parse(byte[] xml) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse workbook XML", e);
        }
    }
}
