package meteordevelopment.meteorclient.gui.renderer.packer;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.Texture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageResize;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TexturePacker {
    private static final int maxWidth = 2048;

    private final List<Image> images = new ArrayList<>();

    public GuiTexture add(Identifier id) {
        try {
            Optional<Resource> optionalRes = mc.getResourceManager().getResource(id);
            if (optionalRes.isEmpty()) {
                MeteorClient.LOG.warn("TexturePacker: Missing texture resource {}", id);
                return null; // skip missing textures
            }

            InputStream in = optionalRes.get().getInputStream();
            GuiTexture texture = new GuiTexture();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer rawImageBuffer = null;

                try {
                    rawImageBuffer = TextureUtil.readResource(in);
                    ((Buffer) rawImageBuffer).rewind();

                    IntBuffer w = stack.mallocInt(1);
                    IntBuffer h = stack.mallocInt(1);
                    IntBuffer ignored = stack.mallocInt(1);

                    ByteBuffer imageBuffer = STBImage.stbi_load_from_memory(rawImageBuffer, w, h, ignored, 4);
                    if (imageBuffer == null) {
                        MeteorClient.LOG.error("TexturePacker: Failed to load image for {}", id);
                        return null;
                    }

                    int width = w.get(0);
                    int height = h.get(0);

                    if (width <= 0 || height <= 0) {
                        MeteorClient.LOG.warn("TexturePacker: Image {} has invalid size {}x{}", id, width, height);
                        STBImage.stbi_image_free(imageBuffer);
                        return null;
                    }

                    TextureRegion region = new TextureRegion(width, height);
                    texture.add(region);

                    images.add(new Image(imageBuffer, region, width, height, true));

                    if (width > 20) addResized(texture, imageBuffer, width, height, 20);
                    if (width > 32) addResized(texture, imageBuffer, width, height, 32);
                    if (width > 48) addResized(texture, imageBuffer, width, height, 48);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    MemoryUtil.memFree(rawImageBuffer);
                }
            }

            return texture;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void addResized(GuiTexture texture, ByteBuffer srcImageBuffer, int srcWidth, int srcHeight, int width) {
        double scaleFactor = (double) width / srcWidth;
        int height = (int) (srcHeight * scaleFactor);

        ByteBuffer imageBuffer = BufferUtils.createByteBuffer(width * height * 4);
        STBImageResize.stbir_resize_uint8(srcImageBuffer, srcWidth, srcHeight, 0, imageBuffer, width, height, 0, 4);

        TextureRegion region = new TextureRegion(width, height);
        texture.add(region);

        images.add(new Image(imageBuffer, region, width, height, false));
    }

    public Texture pack() {
        int width = 0;
        int height = 0;

        int rowWidth = 0;
        int rowHeight = 0;

        // Filter out empty/invalid images
        List<Image> validImages = new ArrayList<>();
        for (Image image : images) {
            if (image != null && image.width > 0 && image.height > 0) validImages.add(image);
            else MeteorClient.LOG.warn("TexturePacker: Skipping empty or invalid image");
        }

        if (validImages.isEmpty()) {
            MeteorClient.LOG.error("TexturePacker: No valid images to pack");
            return null;
        }

        for (Image image : validImages) {
            if (rowWidth + image.width > maxWidth) {
                width = Math.max(width, rowWidth);
                height += rowHeight;

                rowWidth = 0;
                rowHeight = 0;
            }

            image.x = 1 + rowWidth;
            image.y = 1 + height;

            rowWidth += 1 + image.width + 1;
            rowHeight = Math.max(rowHeight, 1 + image.height + 1);
        }

        width = Math.max(width, rowWidth);
        height += rowHeight;

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);

        for (Image image : validImages) {
            byte[] row = new byte[image.width * 4];

            for (int i = 0; i < image.height; i++) {
                ((Buffer) image.buffer).position(i * row.length);
                image.buffer.get(row);

                ((Buffer) buffer).position(((image.y + i) * width + image.x) * 4);
                buffer.put(row);
            }

            ((Buffer) image.buffer).rewind();
            image.free();

            image.region.x1 = (double) image.x / width;
            image.region.y1 = (double) image.y / height;
            image.region.x2 = (double) (image.x + image.width) / width;
            image.region.y2 = (double) (image.y + image.height) / height;
        }

        ((Buffer) buffer).rewind();

        Texture texture = new Texture(width, height, TextureFormat.RGBA8, FilterMode.LINEAR, FilterMode.LINEAR);
        texture.upload(buffer);

        return texture;
    }

    private static class Image {
        public final ByteBuffer buffer;
        public final TextureRegion region;
        public final int width, height;
        public int x, y;
        private final boolean stb;

        public Image(ByteBuffer buffer, TextureRegion region, int width, int height, boolean stb) {
            this.buffer = buffer;
            this.region = region;
            this.width = width;
            this.height = height;
            this.stb = stb;
        }

        public void free() {
            if (stb) STBImage.stbi_image_free(buffer);
        }
    }
}
