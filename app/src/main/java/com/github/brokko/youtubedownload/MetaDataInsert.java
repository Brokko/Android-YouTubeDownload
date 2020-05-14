package com.github.brokko.youtubedownload;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.mp4parser.Box;
import org.mp4parser.Container;
import org.mp4parser.IsoFile;
import org.mp4parser.boxes.apple.AppleArtistBox;
import org.mp4parser.boxes.apple.AppleCoverBox;
import org.mp4parser.boxes.apple.AppleItemListBox;
import org.mp4parser.boxes.apple.AppleNameBox;
import org.mp4parser.boxes.iso14496.part12.ChunkOffsetBox;
import org.mp4parser.boxes.iso14496.part12.FreeBox;
import org.mp4parser.boxes.iso14496.part12.HandlerBox;
import org.mp4parser.boxes.iso14496.part12.MetaBox;
import org.mp4parser.boxes.iso14496.part12.MovieBox;
import org.mp4parser.boxes.iso14496.part12.UserDataBox;
import org.mp4parser.tools.Path;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.List;

/**
 * https://github.com/sannies/mp4parser/blob/master/examples/src/main/java/org/mp4parser/examples/metadata/MetaDataInsert.java (modified)
 * Change metadata and make sure chunkoffsets are corrected.
 */
public class MetaDataInsert extends Thread implements Runnable {

    private final Callback callback;
    private final File videoFile;
    private final String title;
    private final String artist;
    private final String imgURL;

    public MetaDataInsert(Callback callback, File videoFile, String title, String artist, String imgURL) {
        this.callback = callback;
        this.videoFile = videoFile;
        this.title = title;
        this.artist = artist;
        this.imgURL = imgURL;
    }

    @Override
    public void run() {
        try {
            IsoFile isoFile = new IsoFile(videoFile);
            MovieBox moov = isoFile.getBoxes(MovieBox.class).get(0);
            FreeBox freeBox = findFreeBox(moov);

            boolean correctOffset = needsOffsetCorrection(isoFile);
            long sizeBefore = moov.getSize();
            long offset = 0;

            for (Box box : isoFile.getBoxes()) {
                if ("moov".equals(box.getType())) {
                    break;
                }
                offset += box.getSize();
            }

            // Create structure or just navigate to Apple List Box.
            UserDataBox userDataBox;
            if ((userDataBox = Path.getPath(moov, "udta")) == null) {
                userDataBox = new UserDataBox();
                moov.addBox(userDataBox);
            }

            MetaBox metaBox;
            if ((metaBox = Path.getPath(userDataBox, "meta")) == null) {
                metaBox = new MetaBox();
                HandlerBox hdlr = new HandlerBox();
                hdlr.setHandlerType("mdir");
                metaBox.addBox(hdlr);
                userDataBox.addBox(metaBox);
            }

            AppleItemListBox ilst;
            if ((ilst = Path.getPath(metaBox, "ilst")) == null) {
                ilst = new AppleItemListBox();
                metaBox.addBox(ilst);

            }

            if (freeBox == null) {
                freeBox = new FreeBox(128 * 1024);
                metaBox.addBox(freeBox);
            }

            // Got Apple List Box
            AppleNameBox nam;
            if ((nam = Path.getPath(ilst, "©nam")) == null) {
                nam = new AppleNameBox();
            }
            nam.setDataCountry(0);
            nam.setDataLanguage(0);
            nam.setValue(title);
            ilst.addBox(nam);

            AppleArtistBox art;
            if ((art = Path.getPath(ilst, "©ART")) == null) {
                art = new AppleArtistBox();
            }
            art.setDataCountry(0);
            art.setDataLanguage(0);
            art.setValue(artist);
            ilst.addBox(art);

            // Creates a 4:4 image
            Bitmap map = BitmapFactory.decodeStream(new URL(imgURL).openStream());
            Bitmap newMap = Bitmap.createBitmap(map, map.getWidth() / 2 - map.getHeight() / 2, 0, map.getHeight(), map.getHeight());
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            newMap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] byteArray = stream.toByteArray();
            map.recycle();
            newMap.recycle();

            AppleCoverBox cov;
            if ((cov = Path.getPath(ilst, "covr")) == null) {
                cov = new AppleCoverBox();
            }
            cov.setDataCountry(0);
            cov.setDataLanguage(0);
            cov.setPng(byteArray);
            ilst.addBox(cov);

            long sizeAfter = moov.getSize();
            long diff = sizeAfter - sizeBefore;

            // can we compensate by resizing a Free Box we have found?
            if (freeBox.getData().limit() > diff) {
                // either shrink or grow!
                freeBox.setData(ByteBuffer.allocate((int) (freeBox.getData().limit() - diff)));
                sizeAfter = moov.getSize();
                diff = sizeAfter - sizeBefore;
            }

            if (correctOffset && diff != 0)
                correctChunkOffsets(moov, diff);

            BetterByteArrayOutputStream baos = new BetterByteArrayOutputStream();
            moov.getBox(Channels.newChannel(baos));
            isoFile.close();
            FileChannel fc;

            if (diff != 0) {
                // this is not good: We have to insert bytes in the middle of the file
                // and this costs time as it requires re-writing most of the file's data
                fc = splitFileAndInsert(videoFile, offset, sizeAfter - sizeBefore);
            } else {
                // simple overwrite of something with the file
                fc = new RandomAccessFile(videoFile, "rw").getChannel();
            }

            fc.position(offset);
            fc.write(ByteBuffer.wrap(baos.getBuffer(), 0, baos.size()));
            fc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        callback.finish();
    }

    private FileChannel splitFileAndInsert(File f, long pos, long length) throws IOException {
        FileChannel read = new RandomAccessFile(f, "r").getChannel();
        File tmp = File.createTempFile("ChangeMetaData", "splitFileAndInsert");
        FileChannel tmpWrite = new RandomAccessFile(tmp, "rw").getChannel();

        read.position(pos);
        tmpWrite.transferFrom(read, 0, read.size() - pos);
        read.close();

        FileChannel write = new RandomAccessFile(f, "rw").getChannel();
        write.position(pos + length);
        tmpWrite.position(0);

        long transferred = 0;
        while ((transferred += tmpWrite.transferTo(0, tmpWrite.size() - transferred, write)) != tmpWrite.size())
            ;

        tmpWrite.close();
        tmp.delete();

        return write;
    }


    private boolean needsOffsetCorrection(IsoFile isoFile) {
        if (Path.getPath(isoFile, "moov[0]/mvex[0]") != null) {
            // Fragmented files don't need a correction
            return false;
        } else {
            // no correction needed if mdat is before moov as insert into moov want change the offsets of mdat
            for (Box box : isoFile.getBoxes()) {
                if ("moov".equals(box.getType())) {
                    return true;
                }
                if ("mdat".equals(box.getType())) {
                    return false;
                }
            }
            throw new RuntimeException("I need moov or mdat. Otherwise all this doesn't make sense");
        }
    }

    private FreeBox findFreeBox(Container c) {
        for (Box box : c.getBoxes()) {
            if (box instanceof FreeBox) {
                return (FreeBox) box;
            }

            if (box instanceof Container) {
                FreeBox freeBox = findFreeBox((Container) box);
                if (freeBox != null) {
                    return freeBox;
                }
            }
        }
        return null;
    }

    private void correctChunkOffsets(MovieBox movieBox, long correction) {
        List<ChunkOffsetBox> chunkOffsetBoxes = Path.getPaths((Box) movieBox, "trak/mdia[0]/minf[0]/stbl[0]/stco[0]");
        if (chunkOffsetBoxes.isEmpty()) {
            chunkOffsetBoxes = Path.getPaths((Box) movieBox, "trak/mdia[0]/minf[0]/stbl[0]/st64[0]");
        }

        for (ChunkOffsetBox chunkOffsetBox : chunkOffsetBoxes) {
            long[] cOffsets = chunkOffsetBox.getChunkOffsets();
            for (int i = 0; i < cOffsets.length; i++) {
                cOffsets[i] += correction;
            }
        }
    }

    private static class BetterByteArrayOutputStream extends ByteArrayOutputStream {
        byte[] getBuffer() {
            return buf;
        }
    }

    public interface Callback {
        public void finish();
    }
}