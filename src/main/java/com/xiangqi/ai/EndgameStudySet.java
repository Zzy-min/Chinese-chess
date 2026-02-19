package com.xiangqi.ai;

import com.xiangqi.model.Board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 来自 https://www.xqipu.com/canjugupu/1606 的九页残局初始局面（去重后）。
 */
public final class EndgameStudySet {
    private static final Set<String> BOARD_PARTS = new HashSet<String>();
    private static final Map<String, Tier> TIER_MAP = new HashMap<String, Tier>();

    static {
        BOARD_PARTS.add("1C1k1r3/4a4/b4a3/p1N6/2p6/9/9/9/5p3/4K4");
        BOARD_PARTS.add("1C3k3/4P4/b3b4/5C3/9/3n5/9/9/p3p4/5K3");
        BOARD_PARTS.add("1Cbak2n1/4a3R/4b4/1R2p3N/2Nn1cp1r/2C1P4/2r6/4B4/4A4/c3KAB2");
        BOARD_PARTS.add("1N1a5/4c2P1/4ka3/9/9/9/9/9/3K5/9");
        BOARD_PARTS.add("1N3a3/4k4/5a2r/8p/9/9/9/9/1p2C4/3K2B2");
        BOARD_PARTS.add("1n7/4a4/2naCk3/9/5N3/9/9/9/4K4/9");
        BOARD_PARTS.add("1P1P1k3/8r/5P3/2p3N2/9/9/4p4/3A5/4K4/5pB2");
        BOARD_PARTS.add("1r1akab2/9/2n1bcn2/p1p1p1p1p/9/2P3P2/P3P3P/N1C1c3B/9/1RBAKA1N1");
        BOARD_PARTS.add("1r1akab2/9/2ncb1n2/p1p1p1R1p/3r5/2P3P2/Pc2P3P/N1C1C1N2/9/1RBAKAB2");
        BOARD_PARTS.add("1R1akabr1/9/2n1bcn2/p3p1p1p/2p6/2P3P2/P3P3P/N1C1C1N2/2c6/2BAKAB2");
        BOARD_PARTS.add("1r2kabr1/4a4/2n1b2c1/p3p3p/1cp2np2/PR7/2P1P1PRP/N1C1C1N2/9/2BAKAB2");
        BOARD_PARTS.add("1r2kabr1/4a4/n5n1c/pcp1p3p/6b2/2PN5/P3P3P/3CC1N2/8R/1RBAKAB2");
        BOARD_PARTS.add("1r4b2/3kr2PC/3a5/9/9/9/2R6/9/9/5K3");
        BOARD_PARTS.add("1r7/3ka4/2R1ba2r/3CP4/2b5p/1N7/9/9/1p1p2p2/4K4");
        BOARD_PARTS.add("1rb1kabr1/4a4/c1n3n1c/p6Rp/2p1p1p2/9/P1P3P1P/C1N1C1N2/3R5/2BAKAB2");
        BOARD_PARTS.add("1rbak1br1/4a4/n1c1c4/p3C1pCp/9/2p6/P3P1P1P/4B1N2/9/R2AKABR1");
        BOARD_PARTS.add("1rbak2r1/4a4/2n1bcn2/p3p1p1p/2p6/P3P1P2/1cP5P/N1C1C1N2/4A4/1RB1KABR1");
        BOARD_PARTS.add("1rbak3r/4a4/2c1b1n2/p3p1p1p/9/2p3P2/Pc2P3P/N3C1N2/2R6/1RBAKAB2");
        BOARD_PARTS.add("1rbak3r/4a4/2n1b1n2/p3p1p1p/2p6/Pc4P2/1RP1Pc2P/3CC1N2/2N6/2BAKABR1");
        BOARD_PARTS.add("1rbak4/4ac1N1/2n1b1n2/p2N1C3/c5p1R/2pR5/2r3P2/4B4/4A4/2BK1A3");
        BOARD_PARTS.add("1rbakab2/1C7/n2cc1n2/2p1p3p/p5p2/2P6/P3P1P1P/2N1B3C/4A4/1R1AK1BN1");
        BOARD_PARTS.add("1rbakab2/1C7/n2cc1n2/p1p5p/4p1p2/P1P6/4P1P1P/2N3N1C/9/1RBAKAB2");
        BOARD_PARTS.add("1rbakab2/4n1c2/4c1nr1/p1p1p1R1p/9/2C6/P1P1P1P1P/N3C1N2/5R3/2BAKAB2");
        BOARD_PARTS.add("1rbakab2/7r1/2n5c/p1p1p3p/5n3/6R2/PcP1P3P/N1C1C1N2/9/1RBAKAB2");
        BOARD_PARTS.add("1rbakab2/9/2n3n1c/p1p1p3p/1r4p2/P4R3/1cP1P1P1P/N1C1C1N2/1R7/2BAKAB2");
        BOARD_PARTS.add("1rbakab2/9/n2cc1n2/pCp5p/4p1p2/P1P6/4P1P1P/2N3N1C/9/1RBAKAB2");
        BOARD_PARTS.add("1rbakabr1/9/2n3nc1/p1p1p3p/6p2/9/PcP1P1P1P/2NCC1N2/9/1RBAKABR1");
        BOARD_PARTS.add("2b1k4/2P1ar3/5aP2/7N1/9/9/9/9/9/4K4");
        BOARD_PARTS.add("2b1ka3/3Ra4/2n2cR2/p1p1p1p2/5r2p/6P2/P3P3P/4C1N1B/2r1A4/2BAK4");
        BOARD_PARTS.add("2b2k3/4Pn3/b8/7C1/9/9/9/9/5K3/9");
        BOARD_PARTS.add("2b6/1P1k5/b8/3N5/2p6/9/1cP6/9/9/4K4");
        BOARD_PARTS.add("2ba3N1/4k4/3a5/4C1N2/2b6/2p1p1P2/2nc5/4B4/4A2n1/2BAK4");
        BOARD_PARTS.add("2bak2r1/4a4/1c2b2c1/r3C1p1p/pnp4R1/6P2/P1P1P3P/2C6/R8/1NBAKAB2");
        BOARD_PARTS.add("2bak2r1/4a4/4b4/p1p2R3/6r1p/4C1N2/P3P3P/4B4/4AK3/2BA5");
        BOARD_PARTS.add("2bak4/2CP3C1/3ab4/2R6/2n1c4/p8/1n5r1/4B4/4A4/2BAKR3");
        BOARD_PARTS.add("2bak4/4a2R1/4c4/4n3p/4N4/4C1B2/8P/9/4A1r2/2BAK4");
        BOARD_PARTS.add("2bak4/4a4/n3b4/2pC3R1/rc7/9/2Pp5/4B1Cn1/1r2A4/2RAK1B2");
        BOARD_PARTS.add("2bakab2/5R3/n5n1r/p2Rp1pCp/2p4c1/1r4P2/P1P6/4B2C1/4A4/2BA1K3");
        BOARD_PARTS.add("2bakab2/9/n1c1c1n2/p1p1p1p1p/7r1/2P3P2/P3P3P/C1N1B3C/9/1R1AKABN1");
        BOARD_PARTS.add("2bakab2/r2r5/nc2c1n2/2p1p1R1p/p8/9/P1P1P1P1P/NC2C1N2/4A4/R1BAK1B2");
        BOARD_PARTS.add("2bakabc1/n3n4/9/p1P3P1p/4p4/6CN1/P3P3c/2rCB4/4A4/2BAKR3");
        BOARD_PARTS.add("2bk1P1r1/1R7/8b/9/6p2/9/6P2/5A3/4A4/4K4");
        BOARD_PARTS.add("2bnka2N/3Ra4/4b1r2/p5nC1/2p3p2/P8/4P4/B3rA3/9/1R1K5");
        BOARD_PARTS.add("2bnkar2/3RaN3/4b4/p7C/6n2/9/P3P4/4rA3/9/R2K5");
        BOARD_PARTS.add("2bnkar2/3RaN3/4b4/P8/6n1C/9/4P4/R8/4r4/3K2c2");
        BOARD_PARTS.add("2bR1ab2/2N1k4/9/4p3p/p4Cp2/9/P5P1P/4B4/1r2n4/4KABc1");
        BOARD_PARTS.add("2C1kabr1/4a4/1c4nc1/r3p1p1p/p6R1/2pn1NP2/P3P3P/4C4/R8/1NBAKAB2");
        BOARD_PARTS.add("2N2ar2/C4k2n/b6R1/P8/9/9/9/3AB4/6p2/1rp1K1c2");
        BOARD_PARTS.add("2r6/4ak3/5a3/1C1N5/2p6/9/9/B3KA3/9/5p3");
        BOARD_PARTS.add("2rakab2/9/1c2bc3/p5R1p/3n2p2/3N5/P3P3P/B1C1B1Nr1/9/1R1AKA3");
        BOARD_PARTS.add("2rakabr1/9/1c2bc2n/p5p1p/2p2R3/3N2P2/P1P1P3P/1C2B4/9/RN1AKAB2");
        BOARD_PARTS.add("3a1a3/3k2N2/9/9/9/P8/2p6/3A5/rp5C1/4K4");
        BOARD_PARTS.add("3a1k1N1/4a1P2/9/9/9/9/r8/2p5B/8C/3K5");
        BOARD_PARTS.add("3a1k3/4a4/9/9/Crb2N3/4p4/1p7/3K1A2B/2p6/4r1R2");
        BOARD_PARTS.add("3a1k3/6P2/5a2b/2p6/4C4/5N3/9/2pA5/2r4p1/4K4");
        BOARD_PARTS.add("3a1k3/9/3a5/8N/4n4/9/3n5/3A5/4KC3/9");
        BOARD_PARTS.add("3a1kb2/4a4/b1c1c4/p4rCRp/2n1C1P2/1r2P4/P7P/3NB1N2/1R2A4/2BAK4");
        BOARD_PARTS.add("3a1kb2/4arN2/9/1P7/p5R2/1p1R5/2P3p1P/B4A3/3K3c1/r8");
        BOARD_PARTS.add("3a3r1/3ka4/9/p5C2/9/P4N3/9/7p1/3p5/4K4");
        BOARD_PARTS.add("3a4P/3Nnk3/5a2b/4P4/6b2/9/9/9/9/4K4");
        BOARD_PARTS.add("3a5/3ka3r/9/9/9/2R6/9/9/4K3C/9");
        BOARD_PARTS.add("3a5/3Nnk3/b4a3/9/2p1P4/9/2P6/9/9/5KB2");
        BOARD_PARTS.add("3a5/4ak3/n8/2p6/1n1N5/9/6P2/4C4/4K4/9");
        BOARD_PARTS.add("3ak1b1r/4a4/3cb1nc1/6N1p/4C4/P8/1p3R3/2p1C4/2n2K3/2B6");
        BOARD_PARTS.add("3ak1br1/1R2a4/rc2b1nc1/4p1p1p/pnp6/6P2/P1P1P3P/N1C1C1N2/9/2BAKABR1");
        BOARD_PARTS.add("3ak1br1/4a4/1c2b1n1c/r3p1pRp/pnp6/4P1P2/P1P5P/N1C1C1N2/3R5/2BAKAB2");
        BOARD_PARTS.add("3ak1br1/4a4/1c2b1n1c/r3p1pRp/pnp6/5NP2/P1P1P3P/N1C1C4/3R5/2BAKAB2");
        BOARD_PARTS.add("3ak1br1/4a4/1c2b1n1c/r3p1pRp/pnp6/6P2/P1P1P3P/N3C1N2/2CR5/2BAKAB2");
        BOARD_PARTS.add("3ak1br1/4a4/1c2b1nc1/r3p1pRp/p1p6/5NP2/n1P1P3P/N1C1C4/3R5/2BAKAB2");
        BOARD_PARTS.add("3ak1P2/4a2N1/9/pr2p4/9/P7C/9/4B4/4K4/9");
        BOARD_PARTS.add("3ak4/4aP3/9/6p2/8P/9/1pp6/3r1C3/4K4/7C1");
        BOARD_PARTS.add("3aka3/5P3/9/2p1N1p2/1C7/2Brp4/9/4B4/9/4K4");
        BOARD_PARTS.add("3akab2/9/2n1bcn2/p1p1p1p1p/9/2P3P2/c3P3P/2C1C4/9/1NBAKABN1");
        BOARD_PARTS.add("3akabn1/9/1c2b3c/r3p1p1p/pnp6/6P2/P1P1P3P/N1C1C1N2/3R5/2BAKAB2");
        BOARD_PARTS.add("3akabr1/8c/1c2b1n2/r3p1R1p/pnp6/6P2/P1P1P3P/N1C1C1N2/3R5/2BAKAB2");
        BOARD_PARTS.add("3C1k3/4P4/b3b4/5C3/9/3n5/9/9/1p2p4/5K3");
        BOARD_PARTS.add("3k1a3/4a4/3n1C3/6N2/9/6n2/9/4B4/3K5/9");
        BOARD_PARTS.add("3k1a3/4a4/3nC4/6N2/9/6n2/9/3K5/9/9");
        BOARD_PARTS.add("3k1a3/4a4/3nC4/6N2/9/6n2/9/9/9/3K5");
        BOARD_PARTS.add("3k1ab2/2c1a4/2N1b4/7N1/P8/2B6/1p6n/5A3/6C2/2n1K4");
        BOARD_PARTS.add("3k1n3/4aP3/3a5/9/3N5/9/9/9/3K5/9");
        BOARD_PARTS.add("3k1n3/4aP3/3a5/9/3N5/9/9/9/9/3K5");
        BOARD_PARTS.add("3k4c/4P4/2P6/2n3N2/9/9/4p4/5K3/4p4/2B6");
        BOARD_PARTS.add("3k5/2c1P4/9/9/9/3N5/4p4/9/3p5/2B1K1B2");
        BOARD_PARTS.add("3k5/5r3/9/6p2/9/7R1/6P2/3A5/4A4/4K4");
        BOARD_PARTS.add("3nka3/4a4/2P6/9/9/1NB6/9/9/4K4/9");
        BOARD_PARTS.add("3P1k1c1/3P3r1/9/9/6p2/8R/4p4/5A3/9/2B1K1B2");
        BOARD_PARTS.add("3P5/5k3/9/6p2/9/9/9/4p3B/4r4/3K2R2");
        BOARD_PARTS.add("3r1k3/4a4/1R3a3/4CP2p/2R6/2B6/P1P1P3P/9/2c6/2rAK4");
        BOARD_PARTS.add("3r5/4ak3/5a3/2CN5/9/9/9/5p3/9/4K1p2");
        BOARD_PARTS.add("3rkab2/4a4/2n1bc2n/p3p1r2/6p1C/PN2R4/4P1P1P/4B1N2/4A4/3AK1BR1");
        BOARD_PARTS.add("3rkabn1/4a4/1c2b3c/p5p1p/1n7/4N1P2/P1P1P3P/2C1C4/9/RNBAKAB2");
        BOARD_PARTS.add("3rkabr1/4a4/4b1n2/p6cp/1np3p2/1N5R1/P1C1R1P1P/B3C4/4A4/4KAB2");
        BOARD_PARTS.add("4Ca3/1C3k3/3a5/2p5r/5N3/9/9/6p2/4K4/9");
        BOARD_PARTS.add("4k3r/3Pa1PP1/2N2a3/9/8c/9/9/9/5p3/4K4");
        BOARD_PARTS.add("4k4/1P3P3/b8/9/4P4/4p4/9/8B/1p3p3/4K4");
        BOARD_PARTS.add("4k4/5P3/9/4p4/2bP5/3p5/6P2/9/5p3/4K1B2");
        BOARD_PARTS.add("4ka3/1n2a4/3P5/9/9/1N7/9/9/4K4/9");
        BOARD_PARTS.add("4kab2/4a4/2c1b4/2p4Cp/p2rpRP2/8P/P1n1P4/2N1B1C2/1c2A4/2B1KA3");
        BOARD_PARTS.add("4kr3/C3a4/5a3/9/2R6/6Bp1/1p7/5K3/3p5/3p5");
        BOARD_PARTS.add("4nk3/4P4/6c2/6N2/9/9/9/9/9/3K5");
        BOARD_PARTS.add("4P4/5k3/9/9/9/9/5p3/9/4Ar3/2B1K1R2");
        BOARD_PARTS.add("4ra3/4k4/5a3/4C4/9/9/9/3R5/p3K4/3p5");
        BOARD_PARTS.add("4ra3/4k4/5a3/p8/4C4/9/3R5/9/1p2K4/3p5");
        BOARD_PARTS.add("5a2r/4a4/2N2k2b/p8/8p/9/8P/1C7/3p5/4K4");
        BOARD_PARTS.add("5a3/4a1C2/3k5/6N2/9/8P/9/5A1rB/4K4/9");
        BOARD_PARTS.add("5a3/4a4/1N1k1n3/9/9/6C2/9/1p2KA3/3r2C2/2BA2B2");
        BOARD_PARTS.add("5a3/4ak3/9/8p/9/9/8R/4K3C/7r1/9");
        BOARD_PARTS.add("5a3/4ak3/9/9/9/9/9/p1R4C1/7rp/4K4");
        BOARD_PARTS.add("5a3/5k3/5a3/9/4C4/9/9/p3R4/3K5/pr1p5");
        BOARD_PARTS.add("5a3/5k3/5aP2/1C1n5/4C4/9/9/9/2p2p3/3cK4");
        BOARD_PARTS.add("5ab2/3k5/3ab1N2/p6C1/2p1N4/P1B6/cn4n2/9/4A4/2BAK4");
        BOARD_PARTS.add("5k3/4P4/b3b4/5C3/9/9/9/1Cn6/1p2p4/5K3");
        BOARD_PARTS.add("5k3/9/3a5/8N/4n4/9/3n5/3A5/4KC3/9");
        BOARD_PARTS.add("5k3/9/4b3b/9/9/9/9/4K4/1C7/8C");
        BOARD_PARTS.add("5P3/9/2Nk5/9/4P4/9/9/2p6/3r5/4K4");
        BOARD_PARTS.add("5Pb1r/3k5/9/4R4/2b3p2/9/6P2/8B/4A4/4KA3");
        BOARD_PARTS.add("5r3/3ka4/5a3/9/2R6/9/9/5K3/9/6C2");
        BOARD_PARTS.add("5rb2/3ka2PC/8r/R8/9/9/9/9/9/4K4");
        BOARD_PARTS.add("6b2/1C1C5/5k3/9/6b2/9/9/9/4K4/9");
        BOARD_PARTS.add("6b2/1C1C5/b4k3/9/9/9/9/9/4K4/9");
        BOARD_PARTS.add("9/3k3P1/9/4nN3/9/9/9/9/9/4K4");
        BOARD_PARTS.add("9/3k5/1P1a5/9/5P1R1/9/9/6p2/3r5/2B2K3");
        BOARD_PARTS.add("9/3ka4/5a3/p8/9/8R/9/4K4/1prC5/9");
        BOARD_PARTS.add("9/4k4/5P3/7R1/1P2r4/2B6/4p3p/9/9/4K4");
        BOARD_PARTS.add("9/4P3c/3k5/3N2p2/9/9/9/8B/2p5r/4K1BrN");
        BOARD_PARTS.add("9/4P4/5k3/3Nn4/9/9/9/9/9/4K4");
        BOARD_PARTS.add("9/5k3/n2a1a3/9/5N3/5C3/2n6/9/4K4/9");
        BOARD_PARTS.add("9/5P3/3k5/1R7/4r1b2/9/9/9/9/5K3");
        BOARD_PARTS.add("9/n4k3/5a3/9/1n7/5N3/9/4C4/4K4/9");
        BOARD_PARTS.add("C3k4/4ar3/5a3/9/5R3/9/9/9/9/4K4");
        BOARD_PARTS.add("Cc1ak4/C3aP3/3n5/9/9/9/9/6p2/6p2/4K4");
        BOARD_PARTS.add("Cr1a5/6P2/4k4/7N1/9/9/P1P2p1n1/3K1AR1n/1pp1A4/r8");
        BOARD_PARTS.add("N1b2k3/4P4/8b/1cp6/p8/2P6/9/1p7/4K4/9");
        BOARD_PARTS.add("N8/3ka4/2P2P2b/6pcp/9/2B3Pr1/8P/8B/5p3/4KA3");
        BOARD_PARTS.add("P2k1P3/2PCa4/Cr2ba3/9/6b2/9/2p6/9/9/4K4");
        BOARD_PARTS.add("P2P1k3/5nc2/9/6N2/6b2/9/9/9/9/5K3");
        BOARD_PARTS.add("P3k1b2/9/5P3/9/9/9/1p3r1p1/3RB4/4K4/9");
        BOARD_PARTS.add("P3k1b2/9/9/5P3/9/9/1p2r2p1/3RB4/4K4/9");
        BOARD_PARTS.add("P5b2/3k5/5P3/9/9/9/2p1r2p1/1R2B4/4K4/9");
        BOARD_PARTS.add("P5b2/4k4/5P3/9/9/9/2p1r2p1/3RB4/4K4/9");
        BOARD_PARTS.add("P8/6P2/5k3/p1p6/9/p8/4N4/4B4/4K4/c8");
        BOARD_PARTS.add("r1b1ka1r1/4a4/4b2c1/4C1p1p/pnp4R1/6P2/P1P1P3P/2C6/R8/1cBAKAB2");
        BOARD_PARTS.add("r1b1kab2/4a1c2/1cn3n2/p1p1pR2p/6p2/1CPN5/P3P1P1P/4C1N2/7r1/R1BAKAB2");
        BOARD_PARTS.add("r1b1kabr1/4a2c1/1cn6/p1p1n3p/6R2/6p2/P1P3P1P/1CN1C1N2/9/R1BAKAB2");
        BOARD_PARTS.add("r1bak1b2/4a4/n1c1c1n2/p3p1C1p/5Np2/2p6/P3P1P1P/4C4/9/R1BAKABN1");
        BOARD_PARTS.add("r1bak1b2/4a4/n1c1c4/p3C1p1p/9/2p6/P3P1P1P/4C4/9/R1BAKABN1");
        BOARD_PARTS.add("r1bak1br1/4a4/n1c1c4/p3C2Cp/6p2/2p6/P3P1P1P/6N2/9/1RBAKABR1");
        BOARD_PARTS.add("r1bak4/4a4/nc2b1n2/4p1p1p/p1p6/6P2/P1P1P3P/2N1B3C/R3A4/2BAK2N1");
        BOARD_PARTS.add("r1baka3/9/1c2b3c/4n1p1p/pnp6/6P2/P1P1P3P/2C1C4/7R1/1NBAKAB2");
        BOARD_PARTS.add("r1bakab2/9/1cn2cn2/p1p1p1p1p/7r1/6P2/P1P1P3P/N1C1C1N2/9/R1BAKAB1R");
        BOARD_PARTS.add("r1bakabr1/9/1cn1c1n2/p1p1p2Cp/6p2/2P6/P3P1P1P/4C1N2/9/RNBAKABR1");
        BOARD_PARTS.add("r1bakabr1/9/1cn3n1c/p1p1p2Rp/6p2/9/P1P1P1P1P/1CN1C1N2/9/R1BAKAB2");
        BOARD_PARTS.add("r1bakabr1/9/1cn3nc1/p1p1p2Rp/6p2/9/P1P1P1P1P/1CN1C1N2/9/R1BAKAB2");
        BOARD_PARTS.add("r1bakabr1/9/1cn3nc1/p1p1p3p/6p2/2P6/P3P1P1P/1C2C1N2/9/RNBAKABR1");
        BOARD_PARTS.add("r1bakabr1/9/1cn4c1/p1p1p2Rp/5np2/9/P1P1P1P1P/1CN1C1N2/9/R1BAKAB2");
        BOARD_PARTS.add("r1bakabr1/9/2n3nc1/p1p1p3p/4P4/9/PcP1NpPRP/1C2C1N2/9/R1BAKAB2");
        BOARD_PARTS.add("r1bakabr1/9/2n3nc1/p1p1p3p/9/4P1p2/PcP1N2RP/1C2C1N2/9/R1BAKAB2");
        BOARD_PARTS.add("r1bakabr1/9/2n3nc1/p1p1p3p/9/4Pp3/P1P1N1PRP/1C4N2/9/R1BAKAB2");
        BOARD_PARTS.add("r1bakabr1/9/2n4c1/p1p1p3p/5n3/4P4/PcP1N1R1P/1C2C1N2/9/R1BAKAB2");
        BOARD_PARTS.add("r1bakabr1/9/6nc1/4p1p1p/2p6/p4NP2/nRP1P3P/C3C4/9/c1BAKABR1");
        BOARD_PARTS.add("r2akabr1/9/1cn1b1nc1/p1p1p3p/6p2/2P6/P3P1P1P/1CN1C1N2/9/R1BAKABR1");
        BOARD_PARTS.add("r2akabr1/9/2n1b2c1/pc2p3p/5n3/2p3R2/P3P3P/1CN1C1N2/R8/2BAKAB2");
        BOARD_PARTS.add("r3kab2/4a1c2/1cn1b1n2/p1p1pR2p/6p2/1CPN3r1/P3P1P1P/4C1N2/9/R1BAKAB2");
        BOARD_PARTS.add("r3kab2/4a1c2/1cn1b1n2/p1p1pR2p/6p2/2PN3r1/P3P1P1P/3CC1N2/9/R1BAKAB2");
        BOARD_PARTS.add("r3kab2/4a4/2c1b1n2/1R2pN2p/p1p6/6R2/P2r4c/C3C4/9/2BAKAB2");
        BOARD_PARTS.add("r3kab2/4a4/2n1b4/p1p1pR2p/6c2/2PN2p2/P1C1P3P/3CB1N2/4A2r1/1R1A1K2c");
        BOARD_PARTS.add("r3kabn1/4a4/1c2b4/p3p1p1p/1cp6/6P2/P1P1P3P/N3C1N2/9/R1BAKAB2");
        BOARD_PARTS.add("r3kabr1/1c2a1c2/2n1b1n2/p1p1pR2p/6p2/4P4/P1P3P1P/1CN1C1N2/3R5/2BAKAB2");
        BOARD_PARTS.add("r3kabr1/4a4/1c2b1n2/pR2p1p1p/9/2pn2P2/P1P1P2cP/N1C1C1N2/9/2BAKABR1");
        BOARD_PARTS.add("r3kabr1/4a4/2n1b1n2/p3p3p/1cp3pc1/2P1P1PR1/P7P/1CN1C1N2/R8/2BAKAB2");
        BOARD_PARTS.add("r3kabr1/4a4/2n1b1n2/pc2p3p/6pc1/2p3PR1/P3P3P/1CN1C1N2/3R5/2BAKAB2");
        BOARD_PARTS.add("rnbakab2/9/1c2c1n2/p1p1p1p1p/9/2P6/P3P1P1P/1CN3N1C/9/R1BAKABr1");
        BOARD_PARTS.add("rnbakabr1/9/1c2c4/p1p1p2Cp/5np2/2P6/P3P1P1P/4C1N2/9/RNBAKABR1");
    }

    public enum Tier {
        BASIC("初级", 1.00, 0, 800),
        MEDIUM("中级", 1.22, 1, 2200),
        ADVANCED("高级", 1.45, 2, 4200);

        private final String displayName;
        private final double weight;
        private final int depthBonus;
        private final int timeBonusMs;

        Tier(String displayName, double weight, int depthBonus, int timeBonusMs) {
            this.displayName = displayName;
            this.weight = weight;
            this.depthBonus = depthBonus;
            this.timeBonusMs = timeBonusMs;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getWeight() {
            return weight;
        }

        public int getDepthBonus() {
            return depthBonus;
        }

        public int getTimeBonusMs() {
            return timeBonusMs;
        }
    }

    private EndgameStudySet() {
    }

    public static boolean contains(Board board) {
        return board != null && containsFen(FenCodec.toFen(board));
    }

    public static boolean containsFen(String fen) {
        return getTierByFen(fen) != null;
    }

    public static Tier getTier(Board board) {
        return board == null ? null : getTierByFen(FenCodec.toFen(board));
    }

    public static Tier getTierByFen(String fen) {
        if (fen == null) {
            return null;
        }
        String trimmed = fen.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int space = trimmed.indexOf(' ');
        String boardPart = space >= 0 ? trimmed.substring(0, space) : trimmed;
        return TIER_MAP.get(boardPart);
    }

    public static double getTrainingWeight(Board board) {
        Tier tier = getTier(board);
        return tier == null ? 1.0 : tier.getWeight();
    }

    public static Set<String> allBoardParts() {
        return Collections.unmodifiableSet(BOARD_PARTS);
    }

    private static void buildTierMap() {
        if (BOARD_PARTS.isEmpty()) {
            return;
        }
        List<String> ordered = new ArrayList<String>(BOARD_PARTS);
        ordered.sort((a, b) -> {
            int ca = complexityScore(a);
            int cb = complexityScore(b);
            if (ca != cb) {
                return Integer.compare(ca, cb);
            }
            return a.compareTo(b);
        });
        int size = ordered.size();
        int basicCut = size / 3;
        int mediumCut = (size * 2) / 3;
        for (int i = 0; i < size; i++) {
            Tier tier = i < basicCut ? Tier.BASIC : (i < mediumCut ? Tier.MEDIUM : Tier.ADVANCED);
            TIER_MAP.put(ordered.get(i), tier);
        }
    }

    private static int complexityScore(String boardPart) {
        int score = 0;
        for (int i = 0; i < boardPart.length(); i++) {
            char c = boardPart.charAt(i);
            if (!Character.isLetter(c)) {
                continue;
            }
            switch (Character.toLowerCase(c)) {
                case 'k':
                    score += 2;
                    break;
                case 'r':
                case 'c':
                    score += 6;
                    break;
                case 'n':
                    score += 5;
                    break;
                case 'b':
                case 'a':
                    score += 3;
                    break;
                case 'p':
                    score += 2;
                    break;
                default:
                    score += 1;
                    break;
            }
        }
        return score;
    }

    static {
        buildTierMap();
    }
}
