# Caesar and Cleopatra Game Application Design

This document outlines the comprehensive design for the "Caesar and Cleopatra" game application. The design includes detailed descriptions of game mechanics, API endpoints, UI interactions, and special handling for tie conditions and cumulative Vote of Confidence (VOC) resolutions.

---

## 1. Overview

The "Caesar and Cleopatra" game application is a single-page application consisting of a backend implemented in Quarkus (Java v21) and a frontend using static HTML templates updated via JQuery. The game centers on strategic card play, where players use Influence and Action (including Veto) cards, manage resources against various Patrician groups, and engage in VOCs triggered by drawing Bust pieces.

---

## 2. Key Components

### 2.1 Starting Hand
- Each player's starting hand is identical and consists of exactly 6 cards:
  - **5 Influence Cards:** Numbered 1 through 5.
  - **1 Veto Card:** Part of the Action deck.

### 2.2 Action Cards
- In addition to the Veto card, additional Action card types are planned.
- The **playCard** API endpoint will support additional custom parameters for these extra Action cards (e.g., target modifiers, special effects).

### 2.3 Patrician Cards & Groups
- **Patrician Groups:** Each group has an associated number of Patrician cards (either 3 or 5, depending on the group type).
- **VOC Successions:**
  - Patrician cards are awarded to players based on successive VOC outcomes.
  - When all Patrician cards of a given type are acquired by players:
    - All remaining Influence cards for that Patrician type are moved to the discard pile.
    - The corresponding Bust piece is removed from the game and is no longer eligible for drawing.

### 2.4 Bust Pieces
- There are **7 Bust pieces**:
  - 5 corresponding to the Patrician groups.
  - 1 Black.
  - 1 Grey.
- Bust pieces are initially placed in an opaque bag that conceals their colors.
- **Bust Draw Consequences:**
  - **Colored Bust:**
    - Triggers a VOC for the corresponding Patrician group.
    - **VOC Resolution:**
      - Both players reveal any face-down Influence cards placed against that group.
      - Each player's influence is summed.
      - **Standard Outcome:**
        - Both players reveal any face-down Influence cards for that group.
        - Count Philosopher cards for each player; if counts differ, swap winner and loser roles before summing influence.
        - The player with the higher total wins the VOC.
        - In a tie (equal totals): no board cards are removed or discarded; all Influence cards remain face up for cumulative future VOCs.
        - On a clear win:
          - Decrement the board count for this group.
          - The player with the higher total discards their highest value Influence card.
          - The player with the lower total discards their lowest value Influence card.
          - If a player has no numbered influence cards, nothing is discarded for that player.
          - All other Influence cards remain face up on the board for cumulative resolution.
      - **Tie Outcome:**
        - If the VOC results in a tie, no cards are discarded and no Patrician card is awarded.
        - All Influence cards remain on the board face up.
        - Subsequent VOCs for the same group will incorporate both the previously tied influence (carried over) and any newly played Influence cards for a cumulative total.
  - **Grey Bust:**
    - No VOC is triggered; the grey Bust remains visible.
  - **Black Bust:**
    - All visible Bust pieces are returned to the bag.

---

## 3. System Architecture

### 3.1 Backend – Quarkus Application (Java v21)
- **Project Structure:**  
  Divided into separate modules for the backend (Quarkus) and the frontend.
  
- **Game Model Component:**  
  Manages the following:
  - **Patrician Cards:**  
    - Five different types laid out in the common area.
    - Players may play one or more cards against a chosen Patrician group on their turn.
  - **Influence Cards:**
    - **Initial Board Placement:**  
      Each player selects 5 Influence cards from their hand to place face down adjacent to the Patrician groups.
    - **Starting Hand:**  
      As described above.
    - **Remaining Deck Composition:**
      The unplaced Influence cards are shuffled into a separate Influence deck containing five additional sets of Influence cards plus 2 Philosopher cards.
    
    - **Influence Card Limits & Automatic VOC Trigger:**
      - A single player may have no more than five Influence cards played to any given Patrician group. Once this limit is reached, the player cannot play an additional Influence card to that group until a VOC is triggered and one of the Influence cards is discarded.
      - If, at the end of a player's turn, the total number of Influence cards played by both players to a specific Patrician group is eight or more, an additional VOC is automatically triggered. This VOC follows the standard resolution process as if a colored Bust were drawn for that group.
  - **Action Cards:**
    - The remaining Action cards (including future types beyond the Veto card) are shuffled into a separate Action deck.
    - When an Action card is played and its effects are resolved, it is immediately moved to the discard pile.
  - **Deck Presentation:**  
    Distinct decks for Influence and Action with visually differentiable backs.
  - **Discard Pile:**  
    Contains permanently discarded cards (e.g., discarded Action cards and Influence cards from VOCs).
  - **Bust Management:**  
    As outlined in Section 2.4.

### 3.2 REST API Endpoints

- **GET /api/game:**  
  - Used for game initialization or for saving/retrieving the complete game state.
  - Provides:
    - Initial board configuration.
    - For each player:
      - Their persona.
      - **Influence Deck:**  
        - Face-down initial placements of 5 Influence cards.
        - The identical starting hand (5 numbered Influence cards and 1 Veto card).
        - The remaining Influence deck (with additional sets and 2 Philosopher cards).
      - **Action Deck:**  
        - Starting hand’s Veto card and additional Action cards.
      - Visual identifiers for decks.
    - Common area details:
      - Laid-out Patrician cards.
      - A discard pile (initially empty).
      - The current state of the Bust bag (numbers concealed vs. revealed).
    - The active player and current turn phase.

- **GET /api/game/draw:**  
  - Used during a player's turn for drawing a card for hand replenishment.
  - Accepts query parameters:
    - `playerId`: Identifier of the active player.
    - `deckType`: Type of deck (Influence or Action).
  - Always draws from the remaining deck.
  - Returns the drawn card along with a delta update of the turn state.

- **POST /api/game/action/playCard:**  
  - Accepts a JSON payload for playing a card.
  - Parameters include:
    - `playerId`
    - `cardDetails`: Card's number and type (standard Influence, Philosopher, Veto, or other Action types).
    - `target`: The board location for the play.
    - `deckType`: To verify the card’s origin.
    - Additional custom parameters for extra details required by various Action cards.
  - **Behavior:**
    - Influence cards remain on the board until a VOC is triggered.
    - Action cards are discarded immediately after their effects resolve.

- **POST /api/game/action/takeBust:**  
  - Handles drawing a Bust piece during a turn.
  - Returns:
    - The color of the drawn Bust piece.
    - For a **colored Bust**:
      - Automatically calculates and returns the VOC outcome (including sums of revealed Influence card values and identification of winning/losing Influence cards along with the top Patrician card claim).
    - For a **grey Bust**:
      - Indicates no VOC is triggered.
    - For a **black Bust**:
      - Indicates that all visible Bust pieces are returned to the bag.

- **POST /api/game/action/placeInitialInfluence:**  
  - For initial setup.
  - Accepts a JSON payload mapping each chosen Influence card (by number) to a Patrician group.
  - Validates that exactly 5 cards are placed face down.

- **(Optional) POST /api/game/action/selectPersona:**  
  - Supports the selection or updating of a player's persona.

- **Turn Phase Execution:**
  - The API communicates actions as state deltas (e.g., card plays, Bust draws, VOC resolutions, and card draws).
  - The full game state is only sent at initialization or for future save/retrieve functionalities.
  
- **Player Turn Rules - Influence Card Play:**
  - On a player's turn, they must play either one or two Influence cards.
    - If two cards are played, one must be placed face up and the other face down.
    - If only one card is played, it may be placed either face up or face down.
  - No more than two Influence cards can be played by a player on a single turn.

### 3.3 Frontend – Single Page Application (UI)
- **Static UI Templates:**  
  - Served as static resources from `src/main/resources/META-INF/resources`.
  - Dynamically updated with current game state.
  
- **JQuery Integration:**  
  - The UI uses AJAX methods to interact with REST endpoints:
    - **GET /api/game:** Initialization or game state retrieval.
    - **GET /api/game/draw:** For drawing cards during hand replenishment (with parameters `playerId` and `deckType`).
    - **POST /api/game/action/playCard:** For playing cards, including support for additional parameters for various Action cards.
    - **POST /api/game/action/takeBust:** For drawing a Bust piece and receiving VOC outcomes.
    - **POST /api/game/action/placeInitialInfluence:** For submitting starting Influence placements.
    - **POST /api/game/action/selectPersona:** As needed.
  - The UI dynamically displays:
    - Current turn phase information.
    - Card plays.
    - Bust draws and VOC outcomes.
    - Updates to the discard pile and Bust bag state.

---

## 4. High-Level Interaction Diagram

```mermaid
graph TD;
    A[Client Browser (HTML Templates + JQuery)]
    B[HTTP API Layer (REST Endpoints)]
    C[Game Model Component (Advanced Java Beans managing Player Decks, Common Patricians, Discard Pile, Turn Phase & VOC Management, Influence Cards & Bust Setup)]
    D[Quarkus Application Framework]

    A -->|AJAX GET /api/game (initialization)| B
    A -->|AJAX GET /api/game/draw (with playerId & deckType)| B
    A -->|AJAX POST (playCard/takeBust/placeInitialInfluence/selectPersona)| B
    B -->|State delta events: turn actions, VOC outcomes, Bust draws, card draws| C
    B -->|Serve dynamic UI templates| A
```

---

## 5. VOC and Cumulative Tie Resolution

The **Philosopher** influence card inverts the outcome of a VOC when played:
- If the number of Philosopher cards played by both players is equal, no inversion occurs.
- If the counts differ, the winner and loser roles are swapped.

- **Standard VOC Resolution:**  
  When a VOC is triggered (via a colored Bust draw), players reveal their face-down Influence cards for the relevant Patrician group:
  - The player with the higher total influence wins the VOC.
  - The winner discards their highest value Influence card (to the discard pile) and claims the top Patrician card.
  - The loser discards their lowest value Influence card.
  - All other Influence cards remain on the board.

- **Tie Resolution:**
  - If a VOC results in a tie:
    - No Influence cards are discarded.
    - No Patrician card is awarded.
    - All Face-up Influence cards remain on the board.
+ **Philosopher Inversion Handling:**
+  - Before comparing sums, count Philosopher cards played by each player for the group.
+  - If counts differ, swap winner and loser after sum comparison.
+  - Discard policy remains unchanged: the higher-sum player discards highest-value card, and the lower-sum player discards lowest-value card.
  - **Cumulative Mechanism for Future VOCs:**
    - Carried-over Influence cards from the tied VOC are retained on the board.
    - In a subsequent VOC for the same Patrician group, both the carried-over cards and any newly played Influence cards contribute to a cumulative total for each player.
    - This cumulative influence is used to resolve the VOC:
      - If a winner emerges, the standard VOC resolution applies.
      - If the tie persists, the cumulative influence remains on the board for future VOCs.
- **Patrician Group Exhaustion:**
  - Once all Patrician cards of a given type have been awarded:
    - All remaining Influence cards for that group move to the discard pile.
    - The associated Bust piece is removed from the game and will not be drawn again.

---

## 6. Conclusion

This design outlines a robust and flexible game application architecture with:
- A clear separation between frontend and backend components.
- Detailed game mechanics and state management through REST APIs.
- Comprehensive handling of VOC resolutions, including tie scenarios and cumulative influence calculations.
- Support for future enhancements with additional Action card types and extended gameplay mechanics.

This concludes the design document for the Caesar and Cleopatra Game Application.