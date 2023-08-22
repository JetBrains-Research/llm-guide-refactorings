public class Heuristics {
    private void initialize() {
        setText("node");
        Font fb = new Font("Helvetica", Font.BOLD, 12);
        setFont(fb);
        fConnectors = new Vector(4);
        fConnectors.addElement(new LocatorConnector(this, RelativeLocator.north()) );
        fConnectors.addElement(new LocatorConnector(this, RelativeLocator.south()) );
        fConnectors.addElement(new LocatorConnector(this, RelativeLocator.west()) );
        fConnectors.addElement(new LocatorConnector(this, RelativeLocator.east()) );
    }

    protected static Properties getPreferences() {
        if (fPreferences == null) {
            fPreferences= new Properties();
            fPreferences.put("loading", "true");
            fPreferences.put("filterstack", "true");
            InputStream is= null;
            try {
                is= new FileInputStream(getPreferencesFile());
                setPreferences(new Properties(getPreferences()));
                getPreferences().load(is);
            } catch (IOException e) {
                try {
                    if (is != null)
                        is.close();
                } catch (IOException e1) {
                }
            }
        }
        return fPreferences;
    }

    public void execute() {
        // ugly cast to component, but AWT wants and Component instead of an ImageObserver...
        Iconkit r = Iconkit.instance();
        r.registerImage(fImage);
        r.loadRegisteredImages((Component)fView);
        Image image = r.getImage(fImage);
        ImageFigure figure = new ImageFigure(image, fImage, fView.lastClick());
        fView.add(figure);
        fView.clearSelection();
        fView.addToSelection(figure);
        fView.checkDamage();
    }

    private void cityBlockInitialization(ArrayList<IArtifact> newArtifacts,
                                         double[][] coords, double minX, double minY, double range,
                                         double blocks) {
        this.cityBlocks = new ArrayList<CityBlock>();
        for (int i = 0; i < blocks; i++) {
            for (int j = 0; j < blocks; j++) {
                double[] center = { minX + i * (range / 16) + (range / 16) / 2,
                        minY + j * (range / 16) + (range / 16) / 2 };
                CityBlock cityblock = new CityBlock(new Point(i, j));
                cityblock.setCenter(center);
                cityBlocks.add(cityblock);
            }
        }
        double r = range / blocks;
        for (int i = 0; i < newArtifacts.size(); i++) {
            assignArtifactToCityBlock(newArtifacts.get(i), coords[i][0],
                    coords[i][1], minX, minY, blocks, r);
        }
    }

    public void init() {
        fIconkit = new Iconkit(this);

        getContentPane().setLayout(new BorderLayout());

        fView = createDrawingView();

        JPanel attributes = createAttributesPanel();
        attributes.add(new JLabel("Fill"));
        fFillColor = createColorChoice("FillColor");
        attributes.add(fFillColor);

        attributes.add(new JLabel("Text"));
        fTextColor = createColorChoice("TextColor");
        attributes.add(fTextColor);

        attributes.add(new JLabel("Pen"));
        fFrameColor = createColorChoice("FrameColor");
        attributes.add(fFrameColor);

        attributes.add(new JLabel("Arrow"));
        CommandChoice choice = new CommandChoice();
        fArrowChoice = choice;
        choice.addItem(new ChangeAttributeCommand("none",     "ArrowMode", new Integer(PolyLineFigure.ARROW_TIP_NONE),  fView));
        choice.addItem(new ChangeAttributeCommand("at Start", "ArrowMode", new Integer(PolyLineFigure.ARROW_TIP_START), fView));
        choice.addItem(new ChangeAttributeCommand("at End",   "ArrowMode", new Integer(PolyLineFigure.ARROW_TIP_END),   fView));
        choice.addItem(new ChangeAttributeCommand("at Both",  "ArrowMode", new Integer(PolyLineFigure.ARROW_TIP_BOTH),  fView));
        attributes.add(fArrowChoice);

        attributes.add(new JLabel("Font"));
        fFontChoice = createFontChoice();
        attributes.add(fFontChoice);
        getContentPane().add("North", attributes);

        JPanel toolPanel = createToolPalette();
        createTools(toolPanel);
        getContentPane().add("West", toolPanel);

        getContentPane().add("Center", fView);
        JPanel buttonPalette = createButtonPanel();
        createButtons(buttonPalette);
        getContentPane().add("South", buttonPalette);

        initDrawing();
        // JFC should have its own internal double buffering...
        //setBufferedDisplayUpdate();
        setupAttributes();
    }
}