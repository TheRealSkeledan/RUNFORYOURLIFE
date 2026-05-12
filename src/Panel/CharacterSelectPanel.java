package Panel;

import Abstract.Character;
import App.Main;
import Characters.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import javax.swing.*;

public class CharacterSelectPanel extends JPanel {
    private final Main mainFrame;
    private final JLabel portraitLabel;
    private final JLabel iconLabel;
    private final JButton leftButton, rightButton;
    private final String[] characterNames = {"Red", "Green", "Black"};
    private int currentIndex = 0;
    private ImageIcon bgIcon, menuBG;

    public CharacterSelectPanel(Main mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(null);

        bgIcon = new ImageIcon(getClass().getResource("/assets/images/backgrounds/Menu/barThing.png"));
        menuBG = new ImageIcon(getClass().getResource("/assets/images/backgrounds/Menu/menuBG.png"));

        portraitLabel = new JLabel();
        portraitLabel.setBounds(800, 200, 400, 400);
        portraitLabel.setHorizontalAlignment(SwingConstants.CENTER);

        leftButton = new JButton("<");
        leftButton.setBounds(200, 300, 75, 75);
        leftButton.addActionListener(e -> navigateLeft());

        iconLabel = new JLabel();
        iconLabel.setBounds(300, 300, 100, 100);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        rightButton = new JButton(">");
        rightButton.setBounds(450, 300, 75, 75);
        rightButton.addActionListener(e -> navigateRight());

        add(leftButton);
        add(iconLabel);
        add(rightButton);
        add(portraitLabel);

        setupKeyBindings();
        updateCharacterDisplay();
    }

    private void setupKeyBindings() {
        InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "navigateLeft");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "navigateRight");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "confirmSelection");

        actionMap.put("navigateLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                navigateLeft();
            }
        });

        actionMap.put("navigateRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                navigateRight();
            }
        });

        actionMap.put("confirmSelection", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirmSelection();
            }
        });
    }

    private void navigateLeft() {
        currentIndex = (currentIndex - 1 + characterNames.length) % characterNames.length;
        updateCharacterDisplay();
    }

    private void navigateRight() {
        currentIndex = (currentIndex + 1) % characterNames.length;
        updateCharacterDisplay();
    }

    private void updateCharacterDisplay() {
        String characterName = characterNames[currentIndex];
        try {
            String portraitPath = "/assets/images/characters/" + characterName.toLowerCase() + "Portrait.png";
            ImageIcon originalPortraitIcon = new ImageIcon(getClass().getResource(portraitPath));
    
            if (originalPortraitIcon.getIconWidth() == -1 || originalPortraitIcon.getIconHeight() == -1) {
                System.out.println("Image not found: " + portraitPath);
            } else {
                System.out.println("Image found: " + portraitPath);
            }
    
            int desiredHeight = 400;
            int imgWidth = originalPortraitIcon.getIconWidth();
            int imgHeight = originalPortraitIcon.getIconHeight();
    
            int displayWidth = (int) ((double) imgWidth / imgHeight * desiredHeight);
            int displayHeight = desiredHeight;
    
            Image scaledPortrait = originalPortraitIcon.getImage().getScaledInstance(displayWidth, displayHeight, Image.SCALE_SMOOTH);
            ImageIcon portraitIcon = new ImageIcon(scaledPortrait);
            portraitLabel.setIcon(portraitIcon);
    
            int startX = 700; 
            int startY = 200;
            portraitLabel.setBounds(startX, startY, displayWidth, displayHeight);
    
            String iconPath = "/assets/images/characters/" + characterName.toLowerCase() + "Icon.png";
            ImageIcon smallIcon = new ImageIcon(getClass().getResource(iconPath));
            iconLabel.setIcon(smallIcon);
    
            revalidate();
            repaint();
    
            Timer timer = new Timer(5, new ActionListener() {
                int currentX = startX;
    
                @Override
                public void actionPerformed(ActionEvent e) {
                    System.out.println("Timer triggered!");
    
                    if (currentX > 800) {
                        currentX -= 10;
                        portraitLabel.setBounds(currentX, startY, displayWidth, displayHeight);
                        repaint();
                        System.out.println("Current X position: " + currentX);
                    } else {
                        ((Timer) e.getSource()).stop();
                        System.out.println("Animation stopped.");
                    }
                }
            });
    
            System.out.println("Starting timer...");
            timer.start();
    
        } catch (Exception e) {
            System.out.println("Error loading images: " + e.getMessage());
        }
    }           

    private void confirmSelection() {
        try {
            String selectedCharacterName = characterNames[currentIndex];
            Character selected = switch (selectedCharacterName) {
                case "Red" -> new Red(40, 300);
                case "Green" -> new Green(40, 300);
                case "Black" -> new Black(40, 300);
                default -> throw new IllegalStateException("Unexpected character: " + selectedCharacterName);
            };
            mainFrame.startGame(selected);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(menuBG.getImage(), 0, 0, 1280, 720, this);
        g.drawImage(bgIcon.getImage(), 0, 0, 1280, 720, this);
    }
}