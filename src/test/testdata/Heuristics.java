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

    public void onListItemClick(ListView l, View v, int position, long id) {
        if (!isSelectFavoriteMode()) {
            FavouritePoint point = favouritesAdapter.getItem(position);
            LatLon location = new LatLon(point.getLatitude(), point.getLongitude());
            final PopupMenu optionsMenu = new PopupMenu(getActivity(), v);
            DirectionsDialogs.createDirectionActionsPopUpMenu(optionsMenu, location, point, point.getPointDescription(), settings.getLastKnownMapZoom(),
                    getActivity(), true, false);
            optionsMenu.show();
        } else {
            Intent intent = getActivity().getIntent();
            intent.putExtra(SELECT_FAVORITE_POINT_INTENT_KEY, favouritesAdapter.getItem(position));
            getActivity().setResult(SELECT_FAVORITE_POINT_RESULT_OK, intent);
            getActivity().finish();
        }
    }

    private List<PsiFile> createPropertiesFiles() {
        final String name = getBaseName();
        final Set<String> fileNames = ContainerUtil.map2Set(myLocalesModel.getItems(), new Function<Locale, String>() {
            @Override
            public String fun(Locale locale) {
                return locale == PropertiesUtil.DEFAULT_LOCALE ? (name + ".properties") : (name + "_" + locale.toString() + ".properties");
            }
        });
        final List<PsiFile> createdFiles = ApplicationManager.getApplication().runWriteAction(new Computable<List<PsiFile>>() {
            @Override
            public List<PsiFile> compute() {
                return ContainerUtil.map(fileNames, new Function<String, PsiFile>() {
                    @Override
                    public PsiFile fun(String n) {
                        return myDirectory.createFile(n);
                    }
                });
            }
        });
        combineToResourceBundleIfNeed(createdFiles);
        return createdFiles;
    }

    protected void prepareLaunch(File appDir) throws IOException {
        super.doInstall(appDir, getExecutable());
        createInfoPList(appDir);
        generateDsym(appDir, getExecutable(), true);

        if (isDeviceArch(arch)) {
            copyResourcesPList(appDir);
            if (config.isIosSkipSigning()) {
                config.getLogger().warn("Skiping code signing. The resulting app will "
                        + "be unsigned and will not run on unjailbroken devices");
                ldid(getOrCreateEntitlementsPList(true), appDir);
            } else {
                copyProvisioningProfile(provisioningProfile, appDir);
                // sign dynamic frameworks first
                File frameworksDir = new File(appDir, "Frameworks");
                if (frameworksDir.exists() && frameworksDir.isDirectory()) {
                    // Sign swift rt libs
                    for (File swiftLib : frameworksDir.listFiles()) {
                        if (swiftLib.getName().endsWith(".dylib")) {
                            codesign(signIdentity, getOrCreateEntitlementsPList(true), swiftLib);
                        }
                    }

                    // sign embedded frameworks
                    for (File framework : frameworksDir.listFiles()) {
                        if (framework.isDirectory() && framework.getName().endsWith(".framework")) {
                            codesign(signIdentity, getOrCreateEntitlementsPList(true), framework);
                        }
                    }
                }
                // sign the app
                codesign(signIdentity, getOrCreateEntitlementsPList(true), appDir);
            }
        }
    }

    @NotNull
    public static List<IncludedRuntimeModule> loadPluginModules(RuntimeModuleDescriptor mainModule, RuntimeModuleRepository repository) {
        try {
            List<IncludedRuntimeModule> modules = new ArrayList<>();
            Set<String> addedModules = new HashSet<>();
            modules.add(new IncludedRuntimeModuleImpl(mainModule, ModuleImportance.FUNCTIONAL, Collections.emptySet()));
            addedModules.add(mainModule.getModuleId().getStringId());
            try (InputStream inputStream = mainModule.readFile(PLUGIN_XML_PATH)) {
                if (inputStream == null) {
                    throw new MalformedRepositoryException(PLUGIN_XML_PATH + " is not found in " + mainModule.getModuleId().getStringId());
                }
                XMLStreamReader reader = XMLInputFactory.newDefaultFactory().createXMLStreamReader(inputStream);
                int level = 0;
                boolean inContentTag = false;
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        level++;
                        String tagName = reader.getLocalName();
                        if (level == 2 && tagName.equals("content")) {
                            inContentTag = true;
                        }
                        else if (level == 3 && inContentTag && tagName.equals("module")) {
                            String moduleAttribute = XmlStreamUtil.readFirstAttribute(reader, "name");
                            int moduleNameEnd = moduleAttribute.indexOf('/');
                            String moduleName = moduleNameEnd == -1 ? moduleAttribute : moduleAttribute.substring(0, moduleNameEnd);
                            if (addedModules.add(moduleName)) {
                                RuntimeModuleDescriptor module = repository.resolveModule(RuntimeModuleId.raw(moduleName)).getResolvedModule();
                                //todo it makes sense to print something to log if optional module cannot be loaded
                                if (module != null) {
                                    modules.add(new IncludedRuntimeModuleImpl(module, ModuleImportance.OPTIONAL, Collections.emptySet()));
                                }
                            }
                        }
                    }
                    else if (event == XMLStreamConstants.END_ELEMENT) {
                        level--;
                        if (level == 0 || level == 1 && inContentTag) {
                            break;
                        }
                    }
                }
            }
            return modules;
        }
        catch (IOException | XMLStreamException e) {
            throw new MalformedRepositoryException("Failed to load included modules for " + mainModule.getModuleId().getStringId(), e);
        }
    }
}