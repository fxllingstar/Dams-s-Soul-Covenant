package me.st4r.DSC.soul;

import org.bukkit.ChatColor;

@SuppressWarnings("deprecation")
public enum SoulType{
  
    KINDNESS("Kindness", ChatColor.GREEN, 0),
    DETERMINATION("Determination", ChatColor.RED, 0),
    BRAVERY("Bravery", ChatColor.GOLD, 0),
    JUSTICE("Justice", ChatColor.YELLOW, 0),
    PATIENCE("Patience", ChatColor.AQUA, 0),
    INTEGRITY("Integrity", ChatColor.DARK_PURPLE, 0),
    PERSEVERANCE("Perseverance", ChatColor.LIGHT_PURPLE, 0);
    
    private final String displayName;
    private final ChatColor color;
    private final int defaultKarma;

    SoulType(String displayName, ChatColor color, int defaultKarma){
        this.displayName = displayName;
        this.color = color;
        this.defaultKarma = defaultKarma;
    }
    
    public String  getDisplayName(){
        return displayName;
    }

    public ChatColor getColor(){
        return color;
    }

    public int getDefauktKarma(){
        return defaultKarma;
    }

}


