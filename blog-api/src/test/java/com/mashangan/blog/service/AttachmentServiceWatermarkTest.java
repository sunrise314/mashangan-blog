package com.mashangan.blog.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 水印逻辑纯 JVM 验证：不依赖 Spring 上下文与数据库。 */
class AttachmentServiceWatermarkTest {

    private AttachmentService svc() throws Exception {
        AttachmentService svc = new AttachmentService(null, null, null, null, null, null, null);
        Field f = AttachmentService.class.getDeclaredField("watermarkEnabled");
        f.setAccessible(true);
        f.setBoolean(svc, true);
        return svc;
    }

    private byte[] watermark(byte[] bytes, String ext) throws Exception {
        Method m = AttachmentService.class.getDeclaredMethod("applyWatermark", byte[].class, String.class);
        m.setAccessible(true);
        return (byte[]) m.invoke(svc(), bytes, ext);
    }

    private byte[] render(int w, int h, String format, int rgb) throws Exception {
        BufferedImage img = new BufferedImage(w, h, "png".equals(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, rgb);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, format, bos);
        return bos.toByteArray();
    }

    /** 与纯色底差异像素数（阈值 Δ>12），用于判断水印是否画上 */
    private long diffPixels(byte[] img, int rgb) throws Exception {
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(img));
        int r0 = (rgb >> 16) & 0xFF, g0 = (rgb >> 8) & 0xFF, b0 = rgb & 0xFF;
        long diff = 0;
        for (int y = 0; y < bi.getHeight(); y++)
            for (int x = 0; x < bi.getWidth(); x++) {
                int p = bi.getRGB(x, y);
                int dr = Math.abs(((p >> 16) & 0xFF) - r0), dg = Math.abs(((p >> 8) & 0xFF) - g0), db = Math.abs((p & 0xFF) - b0);
                if (dr > 12 || dg > 12 || db > 12) diff++;
            }
        return diff;
    }

    @Test
    void pngGetsWatermark() throws Exception {
        byte[] in = render(600, 400, "png", 0xFF6496C8);
        byte[] out = watermark(in, "png");
        assertNotEquals(java.util.Arrays.hashCode(in), java.util.Arrays.hashCode(out));
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(out));
        assertEquals(600, bi.getWidth());
        assertEquals(400, bi.getHeight());
        assertTrue(diffPixels(out, 0xFF6496C8) > 500, "水印像素数应显著: " + diffPixels(out, 0xFF6496C8));
    }

    @Test
    void jpgGetsWatermark() throws Exception {
        byte[] in = render(600, 400, "jpg", 0xFF6496C8);
        byte[] out = watermark(in, "jpg");
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(out));
        assertEquals(600, bi.getWidth());
        assertEquals(400, bi.getHeight());
        assertTrue(diffPixels(out, 0xFF6496C8) > 500, "水印像素数应显著: " + diffPixels(out, 0xFF6496C8));
    }

    @Test
    void smallImageSkipped() throws Exception {
        byte[] in = render(100, 80, "png", 0xFF6496C8);
        byte[] out = watermark(in, "png");
        assertNotEquals(java.util.Arrays.hashCode(in), 0);
        assertEquals(in.length, out.length, "小图应原样返回");
    }

    @Test
    void svgAndGifSkipped() throws Exception {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes();
        assertEquals(svg.length, watermark(svg, "svg").length);
        byte[] gif = render(600, 400, "png", 0xFF6496C8);
        assertEquals(gif.length, watermark(gif, "gif").length);
    }

    @Test
    void corruptBytesFailOpen() throws Exception {
        byte[] junk = new byte[2048];
        new Random(42).nextBytes(junk);
        byte[] out = watermark(junk, "png");
        assertEquals(junk.length, out.length, "损坏字节应原样返回（fail-open）");
    }
}
