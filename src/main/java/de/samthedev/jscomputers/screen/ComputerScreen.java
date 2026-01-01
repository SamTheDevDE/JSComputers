package de.samthedev.jscomputers.screen;

import de.samthedev.jscomputers.network.ExecuteCommandPacket;
import de.samthedev.jscomputers.network.ToggleComputerPacket;
import de.samthedev.jscomputers.terminal.GraphicsAPI;
import de.samthedev.jscomputers.terminal.Terminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ComputerScreen extends AbstractContainerScreen<ComputerMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("jscomputers", "textures/gui/computer_gui.png");
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;
    
    private static final int SCREEN_AREA_X = 8;
    private static final int SCREEN_AREA_Y = 8;
    private static final int SCREEN_AREA_WIDTH = 240;
    private static final int SCREEN_AREA_HEIGHT = 240;
    // Adjust this to scale the font globally
    private static final float FONT_SCALE = 0.8f;
    private static final int BASE_LINE_HEIGHT = 10;
    private static final int LINE_HEIGHT = Math.round(BASE_LINE_HEIGHT * FONT_SCALE);
    private static final int MAX_WRAP_CHARS = 45;
    
    private static final int CURSOR_WIDTH = 1;
    private static final int CURSOR_HEIGHT = 6;
    
    private int cursorBlink = 0;
    private Button powerButton;
    private Terminal terminal;
    private List<String> displayLines;
    private String currentInput = "";
    private int scrollOffset = 0;
    private final List<String> history = new ArrayList<>();
    private int historyIndex = 0; // points to next slot (history.size()) when not browsing
    private int inputCursorPos = 0; // index in currentInput
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private boolean isSelecting = false;
    // Unified terminal selection across scrollback + current input line
    private int termSelectionStartLine = -1;
    private int termSelectionStartCol = -1;
    private int termSelectionEndLine = -1;
    private int termSelectionEndCol = -1;
    private boolean isTermSelecting = false;
    private int editorScrollOffset = 0;
    private int editorHorizontalScroll = 0;
    // Editor selection
    private int editorSelectionStartRow = -1;
    private int editorSelectionStartCol = -1;
    private int editorSelectionEndRow = -1;
    private int editorSelectionEndCol = -1;
    private boolean isEditorSelecting = false;
    private boolean lastPoweredState = false;
    private boolean awaitingServerOutput = false;

    public ComputerScreen(ComputerMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 256;
        this.imageHeight = 256;
        // Use the terminal from the block entity for persistence
        this.terminal = pMenu.blockEntity != null ? pMenu.blockEntity.getTerminal() : new Terminal();
        this.displayLines = new ArrayList<>();
        // Load history from terminal
        if (this.terminal != null && !this.terminal.getHistory().isEmpty()) {
            this.history.clear();
            this.history.addAll(this.terminal.getHistory());
            this.historyIndex = this.history.size();
        }
    }

    @Override
    protected void init() {
        super.init();
        
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        int buttonWidth = 20;
        int buttonHeight = 20;
        int buttonX = this.leftPos + this.imageWidth / 2 - buttonWidth / 2;
        int buttonY = this.topPos + this.imageHeight + 10; // Below the GUI
        
        this.powerButton = this.addRenderableWidget(
            Button.builder(Component.literal(""), this::onToggleButtonPressed)
                .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
                .build()
        );

        if (this.displayLines.isEmpty()) {
            addWrappedLine("JSComputers Terminal v1.0");
            addWrappedLine("Type 'help' for available commands");
            addWrappedLine("");
        }
    }
    
    
    private String getPrompt() {
        if (terminal == null) return "$ ";
        String dir = terminal.getCurrentDirectory();
        return dir + "$ ";
    }
    
    private int measureWidth(String text) {
        return (int) Math.ceil(this.font.width(text) * FONT_SCALE);
    }
    
    private void drawScaledString(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().scale(FONT_SCALE, FONT_SCALE, 1.0f);
        graphics.drawString(this.font, text, Math.round(x / FONT_SCALE), Math.round(y / FONT_SCALE), color, false);
        graphics.pose().popPose();
    }

    private void renderGraphicsBuffer(GuiGraphics g, GraphicsAPI gfx) {
        if (gfx == null) return;

        int w = gfx.getWidth();
        int h = gfx.getHeight();
        char[][] chars = gfx.getCharBuffer();
        int[][] fg = gfx.getColorBuffer();
        int[][] bg = gfx.getBgColorBuffer();

        float cellW = (float) SCREEN_AREA_WIDTH / w;
        float cellH = (float) SCREEN_AREA_HEIGHT / h;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int x1 = this.leftPos + SCREEN_AREA_X + Math.round(x * cellW);
                int x2 = this.leftPos + SCREEN_AREA_X + Math.round((x + 1) * cellW);
                int y1 = this.topPos + SCREEN_AREA_Y + Math.round(y * cellH);
                int y2 = this.topPos + SCREEN_AREA_Y + Math.round((y + 1) * cellH);
                if (x2 <= x1) x2 = x1 + 1;
                if (y2 <= y1) y2 = y1 + 1;

                int bgColor = 0xFF000000 | (bg[y][x] & 0xFFFFFF);
                int fgColor = 0xFF000000 | (fg[y][x] & 0xFFFFFF);

                g.fill(x1, y1, x2, y2, bgColor);

                char c = chars[y][x];
                if (c != ' ') {
                    drawScaledString(g, String.valueOf(c), x1, y1, fgColor);
                }
            }
        }
    }
    
    private void addWrappedLine(String line) {
        // Pixel-aware wrapping using font metrics
        int maxPixels = SCREEN_AREA_WIDTH - 20;
        int i = 0;
        while (i < line.length()) {
            int j = i + 1;
            int lastFit = i;
            while (j <= line.length()) {
                int w = measureWidth(line.substring(i, j));
                if (w <= maxPixels) {
                    lastFit = j;
                    j++;
                } else {
                    break;
                }
            }
            if (lastFit == i) {
                // Ensure progress even if a single character exceeds maxPixels
                lastFit = Math.min(i + MAX_WRAP_CHARS, line.length());
            }
            displayLines.add(line.substring(i, lastFit));
            i = lastFit;
        }
    }

    private void onToggleButtonPressed(Button button) {
        if (this.menu.blockEntity != null) {
            PacketDistributor.sendToServer(new ToggleComputerPacket(this.menu.blockEntity.getBlockPos()));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        // Don't render default labels
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.menu.isPowered()) {
            int screenLeft = this.leftPos + SCREEN_AREA_X;
            int screenTop = this.topPos + SCREEN_AREA_Y;
            int screenRight = screenLeft + SCREEN_AREA_WIDTH;
            int screenBottom = screenTop + SCREEN_AREA_HEIGHT;
            
            if (mouseX >= screenLeft && mouseX <= screenRight && 
                mouseY >= screenTop && mouseY <= screenBottom) {
                
                // Queue mouse click event to terminal with cell coordinates
                if (terminal != null) {
                    GraphicsAPI gfx = terminal.getGraphics();
                    int relX = (int)(mouseX - screenLeft);
                    int relY = (int)(mouseY - screenTop);
                    // Convert pixel coordinates to cell coordinates
                    int cellX = (int)((relX * gfx.getWidth()) / SCREEN_AREA_WIDTH);
                    int cellY = (int)((relY * gfx.getHeight()) / SCREEN_AREA_HEIGHT);
                    java.util.Map<String, Object> ev = new java.util.HashMap<>();
                    ev.put("x", cellX);
                    ev.put("y", cellY);
                    ev.put("pixelX", relX);
                    ev.put("pixelY", relY);
                    ev.put("button", button);
                    terminal.queueEvent("mouse_click", ev);
                }
                
                this.setFocused(null);
                
                // Handle editor clicks
                if (terminal != null && terminal.isEditorMode()) {
                    List<String> lines = terminal.getEditorLines();
                    int headerHeight = LINE_HEIGHT;
                    int yStart = this.topPos + 15 + headerHeight;
                    int relY = (int)mouseY - yStart;
                    int clickedRow = relY / LINE_HEIGHT + editorScrollOffset;
                    
                    if (clickedRow >= 0 && clickedRow < lines.size()) {
                        String line = lines.get(clickedRow);
                        int localX = (int)mouseX - (this.leftPos + 15 - editorHorizontalScroll);
                        
                        // Find column at X
                        int col = 0;
                        while (col <= line.length()) {
                            int nextX = measureWidth(line.substring(0, col));
                            if (nextX >= localX) break;
                            col++;
                        }
                        col = Math.max(0, Math.min(col, line.length()));
                        
                        terminal.setEditorCursor(clickedRow, col);
                        editorSelectionStartRow = clickedRow;
                        editorSelectionStartCol = col;
                        editorSelectionEndRow = clickedRow;
                        editorSelectionEndCol = col;
                        isEditorSelecting = true;
                    }
                    return true;
                }
                
                // Handle selection in scrollback + current input line
                int listStartY = this.topPos + 15;
                int relY = (int)mouseY - listStartY;
                int clickedLine = relY / LINE_HEIGHT + scrollOffset;
                int totalLines = displayLines.size() + 1; // include current input line as last virtual line
                if (clickedLine >= 0 && clickedLine < totalLines) {
                    String line = getTerminalLine(clickedLine);
                    boolean isInputLine = (clickedLine == displayLines.size());

                    int localX;
                    if (isInputLine) {
                        String prompt = getPrompt();
                        int promptWidth = measureWidth(prompt);
                        String fullLine = prompt + currentInput;
                        int maxWidth = SCREEN_AREA_WIDTH - 20;
                        int cursorPixelPos = measureWidth(fullLine.substring(0, Math.min(fullLine.length(), prompt.length() + inputCursorPos)));
                        int scrollX = 0;
                        if (cursorPixelPos > maxWidth) {
                            scrollX = cursorPixelPos - maxWidth + 10;
                        }
                        localX = (int)(mouseX - (this.leftPos + 15 - scrollX + promptWidth));
                    } else {
                        localX = (int)mouseX - (this.leftPos + 15);
                    }

                    int col = 0;
                    while (col <= line.length()) {
                        int nextX = measureWidth(line.substring(0, col));
                        if (nextX >= localX) break;
                        col++;
                    }
                    col = Math.max(0, Math.min(col, line.length()));

                    termSelectionStartLine = clickedLine;
                    termSelectionStartCol = col;
                    termSelectionEndLine = clickedLine;
                    termSelectionEndCol = col;
                    isTermSelecting = true;

                    if (isInputLine) {
                        inputCursorPos = Math.max(0, Math.min(col, currentInput.length()));
                        selectionStart = inputCursorPos;
                        selectionEnd = inputCursorPos;
                        isSelecting = true;
                    } else {
                        isSelecting = false;
                    }
                    return true;
                }
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.menu.isPowered()) {
            // Handle editor drag
            if (isEditorSelecting && terminal != null && terminal.isEditorMode()) {
                List<String> lines = terminal.getEditorLines();
                int headerHeight = LINE_HEIGHT;
                int yStart = this.topPos + 15 + headerHeight;
                int relY = (int)mouseY - yStart;
                int clickedRow = relY / LINE_HEIGHT + editorScrollOffset;
                clickedRow = Math.max(0, Math.min(clickedRow, lines.size() - 1));
                
                String line = lines.get(clickedRow);
                int localX = (int)mouseX - (this.leftPos + 15 - editorHorizontalScroll);
                
                // Find column at X
                int col = 0;
                while (col <= line.length()) {
                    int nextX = measureWidth(line.substring(0, col));
                    if (nextX >= localX) break;
                    col++;
                }
                col = Math.max(0, Math.min(col, line.length()));
                
                editorSelectionEndRow = clickedRow;
                editorSelectionEndCol = col;
                terminal.setEditorCursor(clickedRow, col);
                return true;
            }
            
            // Handle terminal selection drag (scrollback + input line)
            if (isTermSelecting) {
                int listStartY = this.topPos + 15;
                int relY = (int)mouseY - listStartY;
                int draggedLine = relY / LINE_HEIGHT + scrollOffset;
                int totalLines = displayLines.size() + 1; // include input line
                draggedLine = Math.max(0, Math.min(draggedLine, totalLines - 1));
                String line = getTerminalLine(draggedLine);

                int localX;
                boolean isInputLine = (draggedLine == displayLines.size());
                if (isInputLine) {
                    String prompt = getPrompt();
                    int promptWidth = measureWidth(prompt);
                    String fullLine = prompt + currentInput;
                    int maxWidth = SCREEN_AREA_WIDTH - 20;
                    int cursorPixelPos = measureWidth(fullLine.substring(0, Math.min(fullLine.length(), prompt.length() + inputCursorPos)));
                    int scrollX = 0;
                    if (cursorPixelPos > maxWidth) {
                        scrollX = cursorPixelPos - maxWidth + 10;
                    }
                    localX = (int)(mouseX - (this.leftPos + 15 - scrollX + promptWidth));
                } else {
                    localX = (int)mouseX - (this.leftPos + 15);
                }

                int col = 0;
                while (col <= line.length()) {
                    int nextX = measureWidth(line.substring(0, col));
                    if (nextX >= localX) break;
                    col++;
                }
                col = Math.max(0, Math.min(col, line.length()));

                termSelectionEndLine = draggedLine;
                termSelectionEndCol = col;

                if (isInputLine) {
                    inputCursorPos = Math.max(0, Math.min(col, currentInput.length()));
                    selectionEnd = inputCursorPos;
                    isSelecting = true;
                }
                return true;
            }

            // Handle terminal input drag
            if (isSelecting) {
                int maxVisibleLines = (SCREEN_AREA_HEIGHT - 10) / LINE_HEIGHT;
                int visibleLines = Math.min(displayLines.size() - scrollOffset, maxVisibleLines);
                int inputY = this.topPos + 15 + (visibleLines * LINE_HEIGHT);
                if (mouseY >= inputY - 2 && mouseY <= inputY + LINE_HEIGHT) {
                    String prompt = getPrompt();
                    int promptWidth = measureWidth(prompt);
                    String fullLine = prompt + currentInput;
                    int maxWidth = SCREEN_AREA_WIDTH - 20;
                    int cursorPixelPos = measureWidth(fullLine.substring(0, Math.min(fullLine.length(), prompt.length() + inputCursorPos)));
                    int scrollX = 0;
                    if (cursorPixelPos > maxWidth) {
                        scrollX = cursorPixelPos - maxWidth + 10;
                    }
                    int localX = (int)(mouseX - (this.leftPos + 15 - scrollX + promptWidth));
                    int idx = 0;
                    while (idx <= currentInput.length()) {
                        int nextAcc = measureWidth(currentInput.substring(0, idx));
                        if (nextAcc >= localX) break;
                        idx++;
                    }
                    selectionEnd = Math.max(0, Math.min(idx, currentInput.length()));
                    inputCursorPos = selectionEnd;
                    return true;
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.menu.isPowered()) {
            isSelecting = false;
            isEditorSelecting = false;
            isTermSelecting = false;
            clearTermSelection();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean hasSelection() {
        return selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd;
    }
    
    private boolean hasEditorSelection() {
        return editorSelectionStartRow >= 0 && editorSelectionEndRow >= 0 &&
               (editorSelectionStartRow != editorSelectionEndRow || 
                editorSelectionStartCol != editorSelectionEndCol);
    }

    private boolean hasTermSelection() {
        return termSelectionStartLine >= 0 && termSelectionEndLine >= 0 &&
                (termSelectionStartLine != termSelectionEndLine || termSelectionStartCol != termSelectionEndCol);
    }

    private void clearTermSelection() {
        termSelectionStartLine = -1;
        termSelectionStartCol = -1;
        termSelectionEndLine = -1;
        termSelectionEndCol = -1;
    }

    private String getTerminalLine(int lineIndex) {
        if (lineIndex < displayLines.size()) {
            return displayLines.get(lineIndex);
        }
        // input line
        return getPrompt() + currentInput;
    }

    private String getTermSelectionText() {
        if (!hasTermSelection()) return "";

        int startLine = Math.min(termSelectionStartLine, termSelectionEndLine);
        int endLine = Math.max(termSelectionStartLine, termSelectionEndLine);
        int startCol;
        int endCol;
        boolean forward = (termSelectionStartLine < termSelectionEndLine) ||
                (termSelectionStartLine == termSelectionEndLine && termSelectionStartCol <= termSelectionEndCol);
        if (forward) {
            startCol = termSelectionStartCol;
            endCol = termSelectionEndCol;
        } else {
            startCol = termSelectionEndCol;
            endCol = termSelectionStartCol;
        }

        StringBuilder sb = new StringBuilder();
        int totalLines = displayLines.size() + 1;
        for (int r = startLine; r <= endLine && r < totalLines; r++) {
            String line = getTerminalLine(r);
            if (r == startLine && r == endLine) {
                sb.append(line.substring(Math.min(startCol, line.length()), Math.min(endCol, line.length())));
            } else if (r == startLine) {
                sb.append(line.substring(Math.min(startCol, line.length())));
            } else if (r == endLine) {
                sb.append(line.substring(0, Math.min(endCol, line.length())));
            } else {
                sb.append(line);
            }
            if (r < endLine) sb.append('\n');
        }
        return sb.toString();
    }
    
    private void clearEditorSelection() {
        editorSelectionStartRow = -1;
        editorSelectionStartCol = -1;
        editorSelectionEndRow = -1;
        editorSelectionEndCol = -1;
    }
    
    private String getEditorSelectionText() {
        if (!hasEditorSelection()) return "";
        
        List<String> lines = terminal.getEditorLines();
        int startRow = Math.min(editorSelectionStartRow, editorSelectionEndRow);
        int endRow = Math.max(editorSelectionStartRow, editorSelectionEndRow);
        int startCol, endCol;
        
        if (editorSelectionStartRow < editorSelectionEndRow || 
            (editorSelectionStartRow == editorSelectionEndRow && editorSelectionStartCol <= editorSelectionEndCol)) {
            startCol = editorSelectionStartCol;
            endCol = editorSelectionEndCol;
        } else {
            startRow = editorSelectionEndRow;
            endRow = editorSelectionStartRow;
            startCol = editorSelectionEndCol;
            endCol = editorSelectionStartCol;
        }
        
        if (startRow == endRow) {
            String line = lines.get(startRow);
            return line.substring(Math.min(startCol, line.length()), Math.min(endCol, line.length()));
        }
        
        StringBuilder sb = new StringBuilder();
        for (int r = startRow; r <= endRow && r < lines.size(); r++) {
            String line = lines.get(r);
            if (r == startRow) {
                sb.append(line.substring(Math.min(startCol, line.length())));
            } else if (r == endRow) {
                sb.append(line.substring(0, Math.min(endCol, line.length())));
            } else {
                sb.append(line);
            }
            if (r < endRow) sb.append('\n');
        }
        return sb.toString();
    }
    
    private void deleteEditorSelection() {
        if (!hasEditorSelection()) return;
        
        List<String> lines = terminal.getEditorLines();
        int startRow = Math.min(editorSelectionStartRow, editorSelectionEndRow);
        int endRow = Math.max(editorSelectionStartRow, editorSelectionEndRow);
        int startCol, endCol;
        
        if (editorSelectionStartRow < editorSelectionEndRow || 
            (editorSelectionStartRow == editorSelectionEndRow && editorSelectionStartCol <= editorSelectionEndCol)) {
            startCol = editorSelectionStartCol;
            endCol = editorSelectionEndCol;
        } else {
            startRow = editorSelectionEndRow;
            endRow = editorSelectionStartRow;
            startCol = editorSelectionEndCol;
            endCol = editorSelectionStartCol;
        }
        
        if (startRow == endRow) {
            String line = lines.get(startRow);
            String newLine = line.substring(0, Math.min(startCol, line.length())) + 
                           line.substring(Math.min(endCol, line.length()));
            lines.set(startRow, newLine);
            terminal.setEditorCursor(startRow, startCol);
        } else {
            String firstLine = lines.get(startRow);
            String lastLine = lines.get(endRow);
            String newLine = firstLine.substring(0, Math.min(startCol, firstLine.length())) + 
                           lastLine.substring(Math.min(endCol, lastLine.length()));
            lines.set(startRow, newLine);
            
            for (int r = endRow; r > startRow; r--) {
                lines.remove(r);
            }
            terminal.setEditorCursor(startRow, startCol);
        }
        
        clearEditorSelection();
    }

    private void replaceSelection(String insert) {
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        // Clamp indices to valid bounds
        start = Math.max(0, Math.min(start, currentInput.length()));
        end = Math.max(0, Math.min(end, currentInput.length()));
        currentInput = currentInput.substring(0, start) + insert + currentInput.substring(end);
        inputCursorPos = start + (insert != null ? insert.length() : 0);
        selectionStart = -1;
        selectionEnd = -1;
        isSelecting = false;
    }
    
    private void handleTabCompletion() {
        if (terminal == null || currentInput.isEmpty()) return;
        
        // Parse the input to find what to autocomplete
        String[] parts = currentInput.split(" ");
        String toComplete = parts[parts.length - 1];
        boolean isFirstWord = parts.length == 1;
        
        List<String> matches = new ArrayList<>();
        
        if (isFirstWord) {
            // Autocomplete command names
            String[] commands = {"ls", "cd", "pwd", "mkdir", "cat", "touch", "rm", "echo", 
                               "clear", "help", "tree", "edit", "js", "wget", "grep", "find", 
                               "head", "tail", "wc", "sort", "uniq", "cp", "mv", "date"};
            for (String cmd : commands) {
                if (cmd.startsWith(toComplete)) {
                    matches.add(cmd);
                }
            }
        } else {
            // Autocomplete file/directory names
            String prefix = toComplete;
            String currentDir = terminal.getCurrentDirectory();
            String searchDir = currentDir;
            
            // Handle paths with directory separators
            if (prefix.contains("/")) {
                int lastSlash = prefix.lastIndexOf('/');
                String dirPart = prefix.substring(0, lastSlash + 1);
                prefix = prefix.substring(lastSlash + 1);
                
                if (dirPart.startsWith("/")) {
                    searchDir = dirPart;
                } else {
                    searchDir = currentDir + dirPart;
                }
            }
            
            if (!searchDir.endsWith("/")) searchDir += "/";
            
            // Find matches in the directory
            for (String dir : terminal.getDirectories()) {
                if (dir.startsWith(searchDir) && !dir.equals(searchDir)) {
                    String rel = dir.substring(searchDir.length());
                    int slash = rel.indexOf('/');
                    if (slash != -1) rel = rel.substring(0, slash + 1);
                    if (rel.startsWith(prefix)) {
                        matches.add(rel);
                    }
                }
            }
            
            for (String file : terminal.getFiles().keySet()) {
                if (file.startsWith(searchDir)) {
                    String rel = file.substring(searchDir.length());
                    if (rel.indexOf('/') == -1 && rel.startsWith(prefix)) {
                        matches.add(rel);
                    }
                }
            }
        }
        
        // Apply completion
        if (matches.size() == 1) {
            // Single match - auto complete
            String completion = matches.get(0);
            if (isFirstWord) {
                currentInput = completion;
            } else {
                String beforeLast = currentInput.substring(0, currentInput.lastIndexOf(toComplete));
                currentInput = beforeLast + completion;
            }
            inputCursorPos = currentInput.length();
        } else if (matches.size() > 1) {
            // Multiple matches - show options
            addWrappedLine(getPrompt() + currentInput);
            StringBuilder options = new StringBuilder();
            for (int i = 0; i < matches.size(); i++) {
                if (i > 0) options.append("  ");
                options.append(matches.get(i));
            }
            addWrappedLine(options.toString());
            
            // Find common prefix
            String commonPrefix = matches.get(0);
            for (String match : matches) {
                int i = 0;
                while (i < commonPrefix.length() && i < match.length() && 
                       commonPrefix.charAt(i) == match.charAt(i)) {
                    i++;
                }
                commonPrefix = commonPrefix.substring(0, i);
            }
            
            // Complete to common prefix if longer than current
            if (commonPrefix.length() > toComplete.length()) {
                if (isFirstWord) {
                    currentInput = commonPrefix;
                } else {
                    String beforeLast = currentInput.substring(0, currentInput.lastIndexOf(toComplete));
                    currentInput = beforeLast + commonPrefix;
                }
                inputCursorPos = currentInput.length();
            }
        }
    }
    
    private void executeCommand() {
        if (currentInput.trim().isEmpty()) {
            String prompt = getPrompt();
            addWrappedLine(prompt);
            return;
        }
        
        String prompt = getPrompt();
        addWrappedLine(prompt + currentInput);
        // Track history
        if (!currentInput.trim().isEmpty()) {
            history.add(currentInput);
        }
        historyIndex = history.size();
        // Persist history to terminal
        if (terminal != null) {
            terminal.setHistory(new ArrayList<>(history));
            terminal.markDirty();
        }
        boolean executedLocally = terminal != null;
        if (executedLocally) {
            List<String> output = terminal.executeCommand(currentInput);
            handleCommandOutput(output);
        }

        awaitingServerOutput = !executedLocally;
        if (this.menu.blockEntity != null) {
            PacketDistributor.sendToServer(new ExecuteCommandPacket(this.menu.blockEntity.getBlockPos(), currentInput, awaitingServerOutput));
        }

        currentInput = "";
        inputCursorPos = 0;
    }

    private void handleCommandOutput(List<String> output) {
        if (output == null) {
            return;
        }

        if (output.size() == 1 && "__CLEAR__".equals(output.get(0))) {
            displayLines.clear();
            scrollOffset = 0;
        } else {
            for (String line : output) {
                addWrappedLine(line);
            }
            int maxVisibleLines = (SCREEN_AREA_HEIGHT - 10) / LINE_HEIGHT;
            scrollOffset = Math.max(0, displayLines.size() - maxVisibleLines + 1);
        }
        // Persist history to terminal
        if (terminal != null) {
            terminal.setHistory(new ArrayList<>(history));
            terminal.markDirty();
        }
    }

    public void applyRemoteOutput(List<String> output) {
        if (!awaitingServerOutput) {
            return;
        }
        handleCommandOutput(output);
        awaitingServerOutput = false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // When computer is powered, handle all keys except ESC
        if (this.menu.isPowered()) {
            if (terminal != null && terminal.isEditorMode()) {
                if (keyCode == 256) { // ESC closes GUI
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
                
                // Clipboard & selection shortcuts in editor
                if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                    if (keyCode == GLFW.GLFW_KEY_V) { // Paste
                        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                        if (clip != null && !clip.isEmpty()) {
                            if (hasEditorSelection()) {
                                deleteEditorSelection();
                            }
                            // Insert clipboard content
                            for (char c : clip.toCharArray()) {
                                if (c == '\n') {
                                    terminal.editorNewline();
                                } else if (c >= 32 && c != 127) {
                                    terminal.appendEditorChar(c);
                                }
                            }
                        }
                        return true;
                    }
                    if (keyCode == GLFW.GLFW_KEY_C) { // Copy
                        if (hasEditorSelection()) {
                            String sel = getEditorSelectionText();
                            Minecraft.getInstance().keyboardHandler.setClipboard(sel);
                        }
                        return true;
                    }
                    if (keyCode == GLFW.GLFW_KEY_X) { // Cut
                        if (hasEditorSelection()) {
                            String sel = getEditorSelectionText();
                            Minecraft.getInstance().keyboardHandler.setClipboard(sel);
                            deleteEditorSelection();
                        }
                        return true;
                    }
                    if (keyCode == GLFW.GLFW_KEY_A) { // Select all
                        if (!terminal.getEditorLines().isEmpty()) {
                            editorSelectionStartRow = 0;
                            editorSelectionStartCol = 0;
                            editorSelectionEndRow = terminal.getEditorLines().size() - 1;
                            editorSelectionEndCol = terminal.getEditorLines().get(editorSelectionEndRow).length();
                            terminal.setEditorCursor(editorSelectionEndRow, editorSelectionEndCol);
                        }
                        return true;
                    }
                }
                
                if (keyCode == 259) { // BACKSPACE in editor
                    if (hasEditorSelection()) {
                        deleteEditorSelection();
                    } else {
                        terminal.editorBackspace();
                    }
                    return true;
                }
                if (keyCode == 257) { // ENTER in editor
                    if (hasEditorSelection()) {
                        deleteEditorSelection();
                    }
                    terminal.editorNewline();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_LEFT) { 
                    terminal.editorMoveLeft(); 
                    clearEditorSelection();
                    return true; 
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT) { 
                    terminal.editorMoveRight(); 
                    clearEditorSelection();
                    return true; 
                }
                if (keyCode == GLFW.GLFW_KEY_UP) { 
                    terminal.editorMoveUp(); 
                    clearEditorSelection();
                    return true; 
                }
                if (keyCode == GLFW.GLFW_KEY_DOWN) { 
                    terminal.editorMoveDown(); 
                    clearEditorSelection();
                    return true; 
                }
                if (keyCode == GLFW.GLFW_KEY_HOME) { 
                    terminal.editorMoveLineStart(); 
                    clearEditorSelection();
                    return true; 
                }
                if (keyCode == GLFW.GLFW_KEY_END) { 
                    terminal.editorMoveLineEnd(); 
                    clearEditorSelection();
                    return true; 
                }
                if (keyCode == GLFW.GLFW_KEY_DELETE) { 
                    if (hasEditorSelection()) {
                        deleteEditorSelection();
                    } else {
                        terminal.editorDelete(); 
                    }
                    return true; 
                }
                // CTRL+S to save
                if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == 83) { // 83 = 'S'
                    List<String> out = terminal.saveEditor();
                    for (String s : out) { addWrappedLine(s); }
                    return true;
                }
                // CTRL+Q to quit editor without saving
                if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == 81) { // 81 = 'Q'
                    List<String> out = terminal.quitEditor(false);
                    for (String s : out) { addWrappedLine(s); }
                    return true;
                }
                return true; // consume other keys in editor mode
            }
            if (keyCode == 256) { // ESC - allow closing
                return super.keyPressed(keyCode, scanCode, modifiers);
            }

            // Clipboard & selection shortcuts
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                if (keyCode == GLFW.GLFW_KEY_V) { // Paste
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        if (hasSelection()) {
                            replaceSelection(clip);
                        } else {
                            int pastePos = Math.max(0, Math.min(inputCursorPos, currentInput.length()));
                            currentInput = currentInput.substring(0, pastePos) + clip + currentInput.substring(pastePos);
                            inputCursorPos = pastePos + clip.length();
                        }
                    }
                    return true;
                }
                    if (keyCode == GLFW.GLFW_KEY_C) { // Copy from output or input selection
                        if (hasSelection()) {
                            int selStart = Math.max(0, Math.min(Math.min(selectionStart, selectionEnd), currentInput.length()));
                            int selEnd = Math.max(0, Math.min(Math.max(selectionStart, selectionEnd), currentInput.length()));
                            String sel = currentInput.substring(selStart, selEnd);
                            Minecraft.getInstance().keyboardHandler.setClipboard(sel);
                            return true;
                        }
                        if (hasTermSelection()) {
                            String sel = getTermSelectionText();
                            if (sel != null && !sel.isEmpty()) {
                                Minecraft.getInstance().keyboardHandler.setClipboard(sel);
                            }
                            return true;
                        }
                    }
                if (keyCode == GLFW.GLFW_KEY_A) { // Select all
                    selectionStart = 0;
                    selectionEnd = currentInput.length();
                    inputCursorPos = selectionEnd;
                    return true;
                }
            }
            
            if (keyCode == 259) { // BACKSPACE
                if (hasSelection()) {
                    replaceSelection("");
                } else if (inputCursorPos > 0 && !currentInput.isEmpty()) {
                    int pos = Math.max(0, Math.min(inputCursorPos, currentInput.length()));
                    if (pos > 0) {
                        currentInput = currentInput.substring(0, pos - 1) + currentInput.substring(pos);
                        inputCursorPos = pos - 1;
                    }
                }
                return true;
            }
            if (keyCode == 257) { // ENTER
                executeCommand();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) { // history previous
                if (historyIndex > 0) {
                    historyIndex--;
                    currentInput = history.get(historyIndex);
                    inputCursorPos = currentInput.length();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) { // history next
                if (historyIndex < history.size() - 1) {
                    historyIndex++;
                    currentInput = history.get(historyIndex);
                } else {
                    historyIndex = history.size();
                    currentInput = "";
                }
                inputCursorPos = currentInput.length();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) { // move cursor left
                if (inputCursorPos > 0) inputCursorPos--;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) { // move cursor right
                if (inputCursorPos < currentInput.length()) inputCursorPos++;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) { // move to start
                inputCursorPos = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_END) { // move to end
                inputCursorPos = currentInput.length();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB) { // autocompletion
                handleTabCompletion();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) { // delete at cursor
                if (hasSelection()) {
                    replaceSelection("");
                } else if (inputCursorPos < currentInput.length()) {
                    int pos = Math.max(0, Math.min(inputCursorPos, currentInput.length()));
                    if (pos < currentInput.length()) {
                        currentInput = currentInput.substring(0, pos) + currentInput.substring(pos + 1);
                    }
                }
                return true;
            }
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_LEFT) { // jump word left
                if (inputCursorPos > 0) {
                    int i = inputCursorPos - 1;
                    while (i > 0 && currentInput.charAt(i) == ' ') i--;
                    while (i > 0 && currentInput.charAt(i - 1) != ' ') i--;
                    inputCursorPos = i;
                }
                return true;
            }
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_RIGHT) { // jump word right
                int n = currentInput.length();
                int i = inputCursorPos;
                while (i < n && currentInput.charAt(i) != ' ') i++;
                while (i < n && currentInput.charAt(i) == ' ') i++;
                inputCursorPos = i;
                return true;
            }
            
            // Consume all other key presses to prevent inventory/game keybinds from triggering
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.menu.isPowered()) {
            if (terminal != null && terminal.isEditorMode()) {
                if (codePoint >= 32 && codePoint != 127) {
                    if (hasEditorSelection()) {
                        deleteEditorSelection();
                    }
                    terminal.appendEditorChar(codePoint);
                    return true;
                }
            } else {
                if (codePoint >= 32 && codePoint != 127) {
                    // Clamp cursor position to valid range
                    inputCursorPos = Math.max(0, Math.min(inputCursorPos, currentInput.length()));
                    if (hasSelection()) {
                        replaceSelection(String.valueOf(codePoint));
                    } else {
                        currentInput = currentInput.substring(0, inputCursorPos) + codePoint + currentInput.substring(inputCursorPos);
                        inputCursorPos++;
                    }
                    // Queue a keypress event to terminal so scripts can listen
                    if (terminal != null) {
                        java.util.Map<String, Object> ev = new java.util.HashMap<>();
                        ev.put("key", String.valueOf(codePoint));
                        ev.put("char", String.valueOf(codePoint));
                        ev.put("modifiers", modifiers);
                        terminal.queueEvent("keypress", ev);
                    }
                    return true;
                }
            }
        }
        
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (this.menu.isPowered()) {
            int screenLeft = this.leftPos + SCREEN_AREA_X;
            int screenTop = this.topPos + SCREEN_AREA_Y;
            int screenRight = screenLeft + SCREEN_AREA_WIDTH;
            int screenBottom = screenTop + SCREEN_AREA_HEIGHT;
            if (mouseX >= screenLeft && mouseX <= screenRight && mouseY >= screenTop && mouseY <= screenBottom) {
                if (terminal != null && terminal.isEditorMode()) {
                    int maxVisible = (SCREEN_AREA_HEIGHT - 20) / LINE_HEIGHT;
                    int total = terminal.getEditorLines().size();
                    editorScrollOffset = (int) Math.max(0, Math.min(Math.max(0, total - maxVisible), editorScrollOffset - Math.signum(deltaY)));
                } else {
                    int maxVisible = (SCREEN_AREA_HEIGHT - 10) / LINE_HEIGHT;
                    scrollOffset = (int) Math.max(0, Math.min(Math.max(0, displayLines.size() - maxVisible), scrollOffset - Math.signum(deltaY)));
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        pGuiGraphics.blit(
            TEXTURE, 
            this.leftPos, 
            this.topPos, 
            0, 
            0, 
            this.imageWidth, 
            this.imageHeight, 
            TEXTURE_WIDTH, 
            TEXTURE_HEIGHT
        );
        
        boolean isPowered = this.menu.isPowered();
        
        if (isPowered) {
            // Always fill with terminal background first
            pGuiGraphics.fill(
                this.leftPos + SCREEN_AREA_X,
                this.topPos + SCREEN_AREA_Y,
                this.leftPos + SCREEN_AREA_X + SCREEN_AREA_WIDTH,
                this.topPos + SCREEN_AREA_Y + SCREEN_AREA_HEIGHT,
                0xFF1A1A1A
            );

            boolean graphicsActive = terminal != null && terminal.getGraphics().isActive();
            int displayMode = terminal != null ? terminal.getGraphics().getDisplayMode() : 2;
            
            // Mode 1 = graphics only, Mode 2 = both, Mode 0 = terminal only
            if (graphicsActive && displayMode >= 1) {
                renderGraphicsBuffer(pGuiGraphics, terminal.getGraphics());
            }
            
            int yOffset = 15;
            // Only render terminal/editor if mode is 0 (terminal only) or 2 (both)
            boolean showTerminal = displayMode == 0 || displayMode == 2;
            
            if (terminal != null && terminal.isEditorMode() && showTerminal) {
                // Auto-scroll to keep cursor visible (vertical)
                int cursorRow = terminal.getEditorCursorRow();
                int maxVisibleLines = (SCREEN_AREA_HEIGHT - 20) / LINE_HEIGHT;
                
                // Ensure cursor row is visible
                if (cursorRow < editorScrollOffset) {
                    editorScrollOffset = cursorRow;
                } else if (cursorRow >= editorScrollOffset + maxVisibleLines) {
                    editorScrollOffset = cursorRow - maxVisibleLines + 1;
                }
                
                // Auto-scroll to keep cursor visible (horizontal)
                List<String> lines = terminal.getEditorLines();
                if (cursorRow < lines.size()) {
                    String line = lines.get(cursorRow);
                    int cursorCol = terminal.getEditorCursorCol();
                    int cursorPixelPos = measureWidth(line.substring(0, Math.min(cursorCol, line.length())));
                    int maxWidth = SCREEN_AREA_WIDTH - 30;
                    
                    if (cursorPixelPos < editorHorizontalScroll) {
                        editorHorizontalScroll = Math.max(0, cursorPixelPos - 10);
                    } else if (cursorPixelPos > editorHorizontalScroll + maxWidth) {
                        editorHorizontalScroll = cursorPixelPos - maxWidth + 10;
                    }
                }
                
                // Editor header
                String header = "=== nano: " + terminal.getEditorFilePath() + " ===";
                drawScaledString(pGuiGraphics, header, this.leftPos + 15, this.topPos + yOffset, 0xFFFFFFFF);
                yOffset += LINE_HEIGHT;
                
                // Editor content with scissor clipping
                int start = Math.max(0, editorScrollOffset);
                int end = Math.min(lines.size(), start + maxVisibleLines);
                
                pGuiGraphics.enableScissor(
                    this.leftPos + SCREEN_AREA_X,
                    this.topPos + yOffset - 2,
                    this.leftPos + SCREEN_AREA_X + SCREEN_AREA_WIDTH - 10,
                    this.topPos + SCREEN_AREA_Y + SCREEN_AREA_HEIGHT - 12
                );
                
                // Render selection highlight
                if (hasEditorSelection()) {
                    int startRow = Math.min(editorSelectionStartRow, editorSelectionEndRow);
                    int endRow = Math.max(editorSelectionStartRow, editorSelectionEndRow);
                    int startCol, endCol;
                    
                    if (editorSelectionStartRow < editorSelectionEndRow || 
                        (editorSelectionStartRow == editorSelectionEndRow && editorSelectionStartCol <= editorSelectionEndCol)) {
                        startCol = editorSelectionStartCol;
                        endCol = editorSelectionEndCol;
                    } else {
                        startRow = editorSelectionEndRow;
                        endRow = editorSelectionStartRow;
                        startCol = editorSelectionEndCol;
                        endCol = editorSelectionStartCol;
                    }
                    
                    for (int r = Math.max(start, startRow); r <= Math.min(end - 1, endRow) && r < lines.size(); r++) {
                        String line = lines.get(r);
                        int lineY = this.topPos + 15 + LINE_HEIGHT + ((r - start) * LINE_HEIGHT);
                        int selStartCol = (r == startRow) ? startCol : 0;
                        int selEndCol = (r == endRow) ? endCol : line.length();
                        
                        int x1 = this.leftPos + 15 - editorHorizontalScroll + measureWidth(line.substring(0, Math.min(selStartCol, line.length())));
                        int x2 = this.leftPos + 15 - editorHorizontalScroll + measureWidth(line.substring(0, Math.min(selEndCol, line.length())));
                        
                        pGuiGraphics.fill(x1, lineY - 1, x2, lineY + LINE_HEIGHT, 0x4000FFFF);
                    }
                }
                
                // Render editor lines
                int lineYOffset = yOffset;
                for (int i = start; i < end; i++) {
                    drawScaledString(pGuiGraphics, lines.get(i), this.leftPos + 15 - editorHorizontalScroll, this.topPos + lineYOffset, 0xFFFFFFFF);
                    lineYOffset += LINE_HEIGHT;
                }
                
                pGuiGraphics.disableScissor();
                
                // Status
                String status = "CTRL+S save | CTRL+Q quit";
                drawScaledString(pGuiGraphics, status, this.leftPos + 15, this.topPos + SCREEN_AREA_Y + SCREEN_AREA_HEIGHT - 8, 0xFFFFFFFF);
            } else if (showTerminal) {
                int maxVisibleLines = (SCREEN_AREA_HEIGHT - 10) / LINE_HEIGHT;
                int startLine = Math.max(0, scrollOffset);
                int endLine = Math.min(displayLines.size(), startLine + maxVisibleLines);

                for (int i = startLine; i < endLine; i++) {
                    String line = displayLines.get(i);
                    // Selection highlight for unified terminal selection on scrollback lines
                    if (hasTermSelection()) {
                        int selStartLine = Math.min(termSelectionStartLine, termSelectionEndLine);
                        int selEndLine = Math.max(termSelectionStartLine, termSelectionEndLine);
                        if (i >= selStartLine && i <= selEndLine) {
                            int startCol = (termSelectionStartLine < termSelectionEndLine ||
                                    (termSelectionStartLine == termSelectionEndLine && termSelectionStartCol <= termSelectionEndCol))
                                    ? termSelectionStartCol : termSelectionEndCol;
                            int endCol = (termSelectionStartLine < termSelectionEndLine ||
                                    (termSelectionStartLine == termSelectionEndLine && termSelectionStartCol <= termSelectionEndCol))
                                    ? termSelectionEndCol : termSelectionStartCol;
                            int startColOnLine = (i == selStartLine) ? startCol : 0;
                            int endColOnLine = (i == selEndLine) ? endCol : line.length();
                            startColOnLine = Math.max(0, Math.min(startColOnLine, line.length()));
                            endColOnLine = Math.max(0, Math.min(endColOnLine, line.length()));
                            int x1 = this.leftPos + 15 + measureWidth(line.substring(0, startColOnLine));
                            int x2 = this.leftPos + 15 + measureWidth(line.substring(0, endColOnLine));
                            pGuiGraphics.fill(x1, this.topPos + yOffset - 1, x2, this.topPos + yOffset + LINE_HEIGHT, 0x4000FFFF);
                        }
                    }

                    drawScaledString(pGuiGraphics, line, this.leftPos + 15, this.topPos + yOffset, 0xFFFFFFFF);
                    yOffset += LINE_HEIGHT;
                }
                
                // Render current input with horizontal scrolling if too long
                String prompt = getPrompt();
                String fullLine = prompt + currentInput;
                int maxWidth = SCREEN_AREA_WIDTH - 20;
                int promptWidth = measureWidth(prompt);
                int cursorPixelPos = measureWidth(fullLine.substring(0, Math.min(fullLine.length(), prompt.length() + inputCursorPos)));
                
                int scrollX = 0;
                if (cursorPixelPos > maxWidth) {
                    scrollX = cursorPixelPos - maxWidth + 10;
                }
                
                // Selection highlight for current input line (fallback to terminal selection)
                if (hasTermSelection()) {
                    int selStartLine = Math.min(termSelectionStartLine, termSelectionEndLine);
                    int selEndLine = Math.max(termSelectionStartLine, termSelectionEndLine);
                    if (displayLines.size() >= selStartLine && displayLines.size() <= selEndLine) {
                        int startCol = (termSelectionStartLine < termSelectionEndLine ||
                                (termSelectionStartLine == termSelectionEndLine && termSelectionStartCol <= termSelectionEndCol))
                                ? termSelectionStartCol : termSelectionEndCol;
                        int endCol = (termSelectionStartLine < termSelectionEndLine ||
                                (termSelectionStartLine == termSelectionEndLine && termSelectionStartCol <= termSelectionEndCol))
                                ? termSelectionEndCol : termSelectionStartCol;
                        startCol = Math.max(0, Math.min(startCol, fullLine.length()));
                        endCol = Math.max(0, Math.min(endCol, fullLine.length()));
                        int selX1 = this.leftPos + 15 - scrollX + measureWidth(fullLine.substring(0, startCol));
                        int selX2 = this.leftPos + 15 - scrollX + measureWidth(fullLine.substring(0, endCol));
                        pGuiGraphics.fill(selX1, this.topPos + yOffset - 1, selX2, this.topPos + yOffset + LINE_HEIGHT, 0x4000FFFF);
                    }
                } else if (hasSelection()) {
                    int selStart = Math.max(0, Math.min(Math.min(selectionStart, selectionEnd), currentInput.length()));
                    int selEnd = Math.max(0, Math.min(Math.max(selectionStart, selectionEnd), currentInput.length()));
                    int selX1 = this.leftPos + 15 - scrollX + promptWidth + measureWidth(currentInput.substring(0, selStart));
                    int selX2 = this.leftPos + 15 - scrollX + promptWidth + measureWidth(currentInput.substring(0, selEnd));
                    pGuiGraphics.fill(selX1, this.topPos + yOffset - 1, selX2, this.topPos + yOffset + LINE_HEIGHT, 0x4000FFFF);
                }

                // Clip and draw
                pGuiGraphics.enableScissor(
                    this.leftPos + SCREEN_AREA_X,
                    this.topPos + yOffset - 2,
                    this.leftPos + SCREEN_AREA_X + SCREEN_AREA_WIDTH - 10,
                    this.topPos + yOffset + LINE_HEIGHT
                );
                drawScaledString(pGuiGraphics, fullLine, this.leftPos + 15 - scrollX, this.topPos + yOffset, 0xFFFFFFFF);
                pGuiGraphics.disableScissor();
            }
        }
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        
        // Poll for async output from setTimeout callbacks
        if (terminal != null && this.menu.isPowered()) {
            List<String> asyncOutput = terminal.drainAsyncOutput();
            if (!asyncOutput.isEmpty()) {
                for (String line : asyncOutput) {
                    addWrappedLine(line);
                }
                int maxVisibleLines = (SCREEN_AREA_HEIGHT - 10) / LINE_HEIGHT;
                scrollOffset = Math.max(0, displayLines.size() - maxVisibleLines + 1);
            }
        }
        
        boolean isPowered = this.menu.isPowered();
        
        // Only clear when power transitions from ON to OFF
        if (lastPoweredState && !isPowered) {
            this.cursorBlink = 0;
            this.currentInput = "";
            this.displayLines.clear();
            addWrappedLine("JSComputers Terminal v1.0");
            addWrappedLine("Type 'help' for available commands");
            addWrappedLine("");
            this.scrollOffset = 0;
            clearTermSelection();
        }
        lastPoweredState = isPowered;
        
        if (this.powerButton.visible) {
            int buttonColor = isPowered ? 0xFF00FF00 : 0xFFFF0000; // Green for on, Red for off
            pGuiGraphics.fill(
                this.powerButton.getX(),
                this.powerButton.getY(),
                this.powerButton.getX() + this.powerButton.getWidth(),
                this.powerButton.getY() + this.powerButton.getHeight(),
                buttonColor
            );
        }
        
        this.renderTooltip(pGuiGraphics, pMouseX, pMouseY);

        if (isPowered) {
            this.cursorBlink++;
            boolean showCursor = (this.cursorBlink / 10) % 2 == 0;
            
            if (showCursor) {
                int cursorX;
                int cursorY;
                if (terminal != null && terminal.isEditorMode()) {
                    List<String> lines = terminal.getEditorLines();
                    int start = Math.max(0, editorScrollOffset);
                    int row = terminal.getEditorCursorRow();
                    int col = terminal.getEditorCursorCol();
                    String line = row < lines.size() ? lines.get(row) : "";
                    int relRow = row - start;
                    relRow = Math.max(0, Math.min(relRow, (SCREEN_AREA_HEIGHT - 20) / LINE_HEIGHT));
                    cursorX = 15 - editorHorizontalScroll + measureWidth(line.substring(0, Math.max(0, Math.min(col, line.length()))));
                    cursorY = 15 + LINE_HEIGHT + (relRow * LINE_HEIGHT);
                } else {
                    String prompt = getPrompt();
                    String fullLine = prompt + currentInput;
                    String visiblePrefix = fullLine.substring(0, Math.max(0, Math.min(prompt.length() + inputCursorPos, fullLine.length())));
                    int maxWidth = SCREEN_AREA_WIDTH - 20;
                    int cursorPixelPos = measureWidth(visiblePrefix);
                    
                    // Calculate horizontal scroll offset
                    int scrollX = 0;
                    if (cursorPixelPos > maxWidth) {
                        scrollX = cursorPixelPos - maxWidth + 10;
                    }
                    
                    cursorX = 15 + cursorPixelPos - scrollX;
                    int maxVisibleLines = (SCREEN_AREA_HEIGHT - 10) / LINE_HEIGHT;
                    int visibleLines = Math.min(displayLines.size() - scrollOffset, maxVisibleLines);
                    cursorY = 15 + (visibleLines * LINE_HEIGHT);
                }

                pGuiGraphics.fill(
                    this.leftPos + cursorX,
                    this.topPos + cursorY - CURSOR_HEIGHT + 2,
                    this.leftPos + cursorX + CURSOR_WIDTH,
                    this.topPos + cursorY + CURSOR_HEIGHT,
                    0xFF00FF00
                );
            }
        }
    }
}

