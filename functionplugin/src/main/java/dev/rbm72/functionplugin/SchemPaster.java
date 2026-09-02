package dev.rbm72.functionplugin;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * All WorldEdit API access is isolated here so the classes only load when /build is actually used.
 * FunctionPlugin references this class only after checking isPluginEnabled("WorldEdit"), so a server
 * without WorldEdit never triggers a NoClassDefFoundError. Must run on the main server thread.
 */
final class SchemPaster {

    private SchemPaster() {}

    /** Opaque undo handle handed back to FunctionPlugin (stored as Object there). */
    record PasteHandle(EditSession session, World world) {}

    static PasteHandle paste(Player player, File file, int rotationDegrees) throws IOException, WorldEditException {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IOException("Unrecognized schematic format (expected a .schem)");
        }
        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            clipboard = reader.read();
        }

        Location loc = player.getLocation();
        World weWorld = BukkitAdapter.adapt(player.getWorld());
        EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(weWorld)
                .maxBlocks(-1)
                .build();
        ClipboardHolder holder = new ClipboardHolder(clipboard);
        if (rotationDegrees != 0) {
            holder.setTransform(new AffineTransform().rotateY(rotationDegrees));
        }
        Operation operation = holder
                .createPaste(editSession)
                .to(BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()))
                .ignoreAirBlocks(false)
                .build();
        Operations.complete(operation);
        editSession.close(); // flush the writes; the recorded change set stays for undo
        return new PasteHandle(editSession, weWorld);
    }

    static void undo(PasteHandle handle) {
        try (EditSession undoSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(handle.world())
                .maxBlocks(-1)
                .build()) {
            handle.session().undo(undoSession);
        }
    }
}
