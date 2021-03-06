/*
 * Copyright (C) 2016 Alexander Savelev
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package ru.codemine.pos.ui;

import com.alee.extended.layout.TableLayout;
import com.alee.extended.layout.ToolbarLayout;
import com.alee.extended.statusbar.WebMemoryBar;
import com.alee.extended.statusbar.WebStatusBar;
import com.alee.extended.statusbar.WebStatusLabel;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebTextField;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.ImageIcon;
import javax.swing.WindowConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.codemine.pos.application.Application;
import ru.codemine.pos.entity.Product;
import ru.codemine.pos.exception.GeneralException;
import ru.codemine.pos.exception.WorkdayAlreadyOpenedException;
import ru.codemine.pos.service.ProductService;
import ru.codemine.pos.service.UserService;
import ru.codemine.pos.service.WorkdayService;
import ru.codemine.pos.ui.salespanel.SalesPanel;

/**
 *
 * @author Alexander Savelev
 */

@Component
public class MainWindow extends WebFrame
{
    @Autowired private Application application;
    @Autowired private UserService userService;
    @Autowired private WorkdayService workdayService;
    @Autowired private ProductService productService;
    
    private final WebTabbedPane tabs;
    private final SalesPanel salesPanel;
    private final WebStatusBar statusBar;
    
    private boolean inputBlocked;
    
    public MainWindow()
    {
        setTitle("Торговая касса v0.1alpha ");
        setSize(1024, 768);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        inputBlocked = true;
        
        salesPanel = new SalesPanel();
        
        tabs = new WebTabbedPane(WebTabbedPane.BOTTOM);
        tabs.addTab("Продажи", new ImageIcon("images/icons/32x32/Coffee-01.png"), salesPanel);
        tabs.addTab("Документы", new ImageIcon("images/icons/32x32/Library-01.png"), new WebPanel());
        tabs.addTab("Отчеты", new ImageIcon("images/icons/32x32/Document-01.png"), new WebPanel());
        tabs.addTab("Настройки", new ImageIcon("images/icons/32x32/Gear-01.png"), new WebPanel());
        
        //tabs.setRound(StyleConstants.largeRound);
        
        statusBar = new WebStatusBar();
        statusBar.add(new WebStatusLabel("Статус: готов"));
        statusBar.add(new WebMemoryBar(), ToolbarLayout.END);
        
        TableLayout layout = new TableLayout(new double[][]{
            {TableLayout.FILL}, {TableLayout.FILL, 15, TableLayout.PREFERRED}
        });
        setLayout(layout);
        
        add(tabs, "0, 0");
        add(statusBar, "0, 2");
        
        setupActionListeners();
        setupKeyboard();
        
    }
    
    public void showMainWindow()
    {
        refreshStatus();
        setVisible(true);
        inputBlocked = false;
    }
    
    public void refreshStatus()
    {

        salesPanel.getUpperStatusPanel().setCurrentUserStatus(application.getActiveUser().getUsername());
        salesPanel.getUpperStatusPanel().setRightTopStatus(workdayService.isWorkdayOpened() ? "Смена открыта" : "Смена закрыта");
        salesPanel.getUpperStatusPanel().setLeftBottomStatus("Статус чека итд.");

    }
    
    private void setupActionListeners()
    {
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                if(WebOptionPane.showConfirmDialog(rootPane, 
                        "Вы хотите выйти?", "Подтвердите действие", 
                        WebOptionPane.YES_NO_OPTION, WebOptionPane.QUESTION_MESSAGE) == 0)
                {
                    application.close();
                }
                
            }
        });
        
        salesPanel.getButtonsPanel().getOpenWorkdayButton().addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                try
                {
                    if(WebOptionPane.showConfirmDialog(rootPane, 
                        "Открыть новую смену?", "Подтвердите действие", 
                        WebOptionPane.YES_NO_OPTION, WebOptionPane.QUESTION_MESSAGE) == 0)
                    {
                        workdayService.openNewWorkday();
                    }
                    
                    refreshStatus();
                } 
                catch (WorkdayAlreadyOpenedException | GeneralException ex)
                {
                    WebOptionPane.showMessageDialog(rootPane, ex.getLocalizedMessage(), "Ошибка", WebOptionPane.WARNING_MESSAGE);
                }
            }
        });
        
        salesPanel.getButtonsPanel().getCloseWorkdayButton().addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                try
                {
                    if(WebOptionPane.showConfirmDialog(rootPane, 
                        "Напечатать Z-отчет?", "Подтвердите действие", 
                        WebOptionPane.YES_NO_OPTION, WebOptionPane.QUESTION_MESSAGE) == 0)
                    {
                        workdayService.closeWorkday();
                    }
                    
                    refreshStatus();
                } 
                catch (GeneralException ex)
                {
                    WebOptionPane.showMessageDialog(rootPane, ex.getLocalizedMessage(), "Ошибка", WebOptionPane.WARNING_MESSAGE);
                }
            }
        });
        
    }
    
    

    private void setupKeyboard()
    {
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(new KeyDispatcher());
    }
    
    private class KeyDispatcher implements KeyEventDispatcher
    {

        @Override
        public boolean dispatchKeyEvent(KeyEvent e)
        {
            WebTextField inputField = salesPanel.getChequeSetupPanel().getInputField();
            
            //Если на вводе число, при этом активно и является видимым окно набора чеков - 
            //вводим данное число в строку поиска
            if(tabs.getSelectedIndex() == 0 && !inputBlocked
                    && ("1234567890".indexOf(e.getKeyChar()) >= 0)) 
            {
                e.setSource(inputField);
            }
            //Если на вводе Enter и в строке поиска что-то есть
            //Ищем по ШК позицию и вставляем в чек
            else if(tabs.getSelectedIndex() == 0 && !inputBlocked && !"".equals(inputField.getText())                    && (e.getKeyCode() == KeyEvent.VK_ENTER))
            {
                String barcode = inputField.getText();
                inputField.clear();
                Product product = productService.getByBarcode(barcode);
                if(product != null)
                {
                    WebOptionPane.showMessageDialog(rootPane, "Товар найден: " + product.getName());
                }
                else
                {
                    WebOptionPane.showMessageDialog(rootPane, "Товар по штрихкоду " + barcode + " не найден!");
                }
            }
            
            return false;
        }
        
    }

}
