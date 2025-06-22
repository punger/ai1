// Main game logic and state management
window.GameCore = {
    
    // Track influence cards placed during standard play
    standardPlayInfluencePlacements: [],
    
    init: function() {
        this.bindEventHandlers();
        this.refreshGameState();
    },

    loadTemplate: function(templatePath) {
        return $.get(templatePath);
    },

    renderActionButtons: function(buttonConfigs) {
        const self = this;
        self.loadTemplate('templates/action-buttons.hbs')
            .then(function(templateSource) {
                const buttonTemplate = Handlebars.compile(templateSource);
                $('#action-buttons').html(buttonTemplate({ buttons: buttonConfigs }));
                
                // Re-bind event handlers for new buttons
                self.bindActionButtonEvents();
            });
    },

    clearActionButtons: function() {
        $('#action-buttons').empty();
    },

    refreshGameState: function() {
        const self = this;
        axios.get('/game')
            .then(({ data }) => {
                const current = data.currentPlayer; // "CAESAR" or "CLEOPATRA"
                const opponent = current === 'CAESAR' ? 'CLEOPATRA' : 'CAESAR';
                const patricianBoard = data.patricianBoard;
                const gameMode = data.gameMode;
                const waitingForInitialInfluence = data.waitingForInitialInfluence;

                // Update game status and turn info
                if (gameMode === 'INITIAL_INFLUENCE_PLACEMENT') {
                    $('#game-status').text(`Initial Influence Placement Phase`);
                    if (waitingForInitialInfluence) {
                        $('#turn-info').text(`${current}'s turn to place initial influence`);
                        // Show submit initial influence button and auto-complete button (for testing)
                        self.renderActionButtons([{
                            id: 'submit-influence',
                            text: 'Submit Initial Influence',
                            cssClass: 'primary-button',
                            disabled: true
                        }, {
                            id: 'auto-complete-influence',
                            text: 'Auto-Complete (Test)',
                            cssClass: 'secondary-button'
                        }]);
                    } else {
                        $('#turn-info').text(`Waiting for ${current} to place initial influence`);
                        self.clearActionButtons();
                    }
                } else if (gameMode === 'STANDARD_PLAY') {
                    $('#game-status').text(`Standard Play - ${current}'s turn`);
                    $('#turn-info').text('Select cards to play');
                    
                    // Show appropriate action buttons for standard play
                    const buttons = [];
                    
                    // If player has placed influence cards, show End Turn button
                    if (self.standardPlayInfluencePlacements.length > 0) {
                        buttons.push({
                            id: 'end-turn',
                            text: 'End Turn',
                            cssClass: 'primary-button'
                        });
                    }
                    
                    // Always show Skip to Action Phase if no cards placed yet
                    if (self.standardPlayInfluencePlacements.length === 0) {
                        buttons.push({
                            id: 'skip-to-action',
                            text: 'Skip to Action Phase',
                            cssClass: 'secondary-button'
                        });
                    }
                    
                    if (buttons.length > 0) {
                        self.renderActionButtons(buttons);
                    } else {
                        self.clearActionButtons();
                    }
                }

                // Store current player for submit action
                window.currentPlayer = current;
                window.gameMode = gameMode;
                window.waitingForInitialInfluence = waitingForInitialInfluence;

                const patricians = Object.keys(patricianBoard).map(key => {
                    const entry = patricianBoard[key];
                    
                    // Process opponent influence cards - show backs when face down
                    const oppInf = (entry.influence[opponent] || []).map(i => {
                        if (i.faceUp) {
                            return 'influence/' + window.GameUtils.cardFileName(i.type);
                        } else {
                            // Show appropriate card back for opponent
                            return 'backs/' + opponent.toLowerCase() + '.svg';
                        }
                    });
                    
                    // Process current player influence cards - show backs when face down, but store actual card data
                    const playerInf = (entry.influence[current] || []).map(i => {
                        if (i.faceUp) {
                            return {
                                src: 'influence/' + window.GameUtils.cardFileName(i.type),
                                actualCard: null,
                                faceUp: true
                            };
                        } else {
                            // Show appropriate card back for current player, but store the actual card data for hover
                            return {
                                src: 'backs/' + current.toLowerCase() + '.svg',
                                actualCard: 'influence/' + window.GameUtils.cardFileName(i.type),
                                faceUp: false
                            };
                        }
                    });

                    return {
                        name: window.GameUtils.capitalize(key.toLowerCase()),
                        image: key.toLowerCase() + '.svg',
                        opponentInfluence: oppInf,
                        playerInfluence: playerInf
                    };
                });

                // Load and render patrician board template
                self.loadTemplate('templates/patrician-column.hbs')
                    .then(function(templateSource) {
                        const boardTemplate = Handlebars.compile(templateSource);
                        $('#patrician-board-area').html(boardTemplate({ patricians }));
                        
                        // Use setTimeout to ensure DOM is fully ready
                        setTimeout(function() {
                            console.log('Setting up drag and drop...');
                            self.setupDragAndDrop();
                            self.setupInfluenceStackHover();
                            self.setupFaceDownCardHover();
                        }, 100);
                    });

                // Build and render current player's hand
                if (waitingForInitialInfluence && gameMode === 'INITIAL_INFLUENCE_PLACEMENT') {
                    // During initial influence placement, show the standard starting cards
                    const handCards = ['influence/one.svg', 'influence/two.svg', 'influence/three.svg', 'influence/four.svg', 'influence/five.svg'];
                    self.renderHand(handCards);
                } else if (gameMode === 'STANDARD_PLAY') {
                    // During standard play, show the actual hand from the backend
                    const handCards = (data.currentPlayerHand || []).map(card => {
                        if (card.type === 'influence') {
                            return 'influence/' + card.id + '.svg';
                        } else if (card.type === 'action') {
                            return 'action/' + card.id.toLowerCase() + '.svg';
                        }
                        return 'influence/' + card.id + '.svg'; // Default to influence folder
                    });
                    self.renderHand(handCards);
                } else {
                    // Clear hand if not this player's turn for initial influence
                    $('#hand-area').empty();
                }

                // Update deck counts
                self.updateDeckCounts(data, current, opponent);
                
                window.GameUtils.updateSubmitButton();
            })
            .catch(err => console.error('Failed to load game state', err));
    },

    updateDeckCounts: function(data, current, opponent) {
        // Update player deck counts
        const playerHand = data.currentPlayerHand || [];
        $('#player-action-count').text(data.playerActionDeckSize || 0);
        $('#player-influence-count').text(data.playerInfluenceDeckSize || 0);
        $('#discard-pile-count').text(data.discardPileSize || 0);
        $('#bust-bag-count').text(data.bustBagSize || 0);

        // Update opponent deck counts (these would come from the backend)
        $('#opponent-hand-count').text(data.opponentHandSize || 0);
        $('#opponent-action-count').text(data.opponentActionDeckSize || 0);
        $('#opponent-influence-count').text(data.opponentInfluenceDeckSize || 0);
    },

    renderHand: function(handCards) {
        const self = this;
        this.loadTemplate('templates/hand-card.hbs')
            .then(function(templateSource) {
                const handTemplate = Handlebars.compile(templateSource);
                $('#hand-area').html(handTemplate({ hand: handCards }));
                
                // Use setTimeout to ensure DOM is fully ready
                setTimeout(function() {
                    // Enable drag-and-drop for influence cards in hand
                    const $handImages = $('#hand-area img');
                    console.log('Making', $handImages.length, 'hand cards draggable');
                    window.GameUtils.makeDraggable($handImages);
                    
                    // During standard play, also make the hand cards clickable for additional interactions
                    if (window.gameMode === 'STANDARD_PLAY') {
                        $handImages.css('cursor', 'grab');
                    }
                }, 100);
            });
    },

    setupDragAndDrop: function() {
        const self = this;
        
        // Make current player's influence zones accept dropped cards
        $('.player-influence').droppable({
            accept: '#hand-area img',
            hoverClass: 'droppable-hover',
            drop: function (event, ui) {
                console.log('Card dropped');
                const $target = $(this);
                const $card = $(ui.draggable);
                const patricianName = $target.closest('.patrician-column').find('h3').text().trim().toUpperCase();

                // Get or create the influence stack
                let $stack = $target.find('.influence-stack');
                if ($stack.length === 0) {
                    $stack = $('<div class="influence-stack"></div>');
                    $target.append($stack);
                }

                // If this is initial influence placement, only allow one card per patrician
                if (window.gameMode === 'INITIAL_INFLUENCE_PLACEMENT') {
                    // Return existing card to hand if present
                    const $existing = $stack.find('.influence-card').first();
                    if ($existing.length) {
                        console.log('Existing card found, returning to hand');
                        const $returnCard = $('<img>')
                            .attr('src', $existing.attr('src'))
                            .attr('alt', 'Influence')
                            .attr('width', '100')
                            .attr('height', '300');
                        $('#hand-area').append($returnCard);
                        window.GameUtils.makeDraggable($returnCard);
                        $existing.remove();
                    }
                    
                    // Clear the stack
                    $stack.empty();
                } else if (window.gameMode === 'STANDARD_PLAY') {
                    // During standard play, limit to 2 influence cards max
                    if (self.standardPlayInfluencePlacements.length >= 2) {
                        alert('You can only place up to 2 influence cards per turn');
                        return;
                    }
                }

                // Remove original card from hand FIRST to prevent duplicates
                const originalCardSrc = $card.attr('src');
                $card.remove();

                // Determine if card should be face down based on game mode and placement order
                let displaySrc = originalCardSrc;
                let isFaceDown = false;
                
                if (window.gameMode === 'INITIAL_INFLUENCE_PLACEMENT') {
                    // During initial influence placement, all cards should be placed face down
                    displaySrc = 'cards/backs/' + window.currentPlayer.toLowerCase() + '.svg';
                    isFaceDown = true;
                } else if (window.gameMode === 'STANDARD_PLAY') {
                    // During standard play, first card face down, second face up
                    const isFaceUp = self.standardPlayInfluencePlacements.length > 0;
                    if (!isFaceUp) {
                        displaySrc = 'cards/backs/' + window.currentPlayer.toLowerCase() + '.svg';
                        isFaceDown = true;
                    }
                }

                // Create the new card element with proper positioning
                const $newCard = $('<img>')
                    .attr('src', displaySrc)
                    .attr('alt', 'Influence')
                    .attr('width', '100')
                    .attr('height', '300')
                    .addClass('influence-card')
                    .css({
                        position: 'absolute',
                        top: '0px',
                        left: '0px',
                        opacity: '1'
                    });

                // Store the actual card data for hover functionality if face down
                if (isFaceDown) {
                    $newCard.attr('data-actual-card', originalCardSrc);
                    $newCard.attr('data-face-up', 'false');
                } else {
                    $newCard.attr('data-face-up', 'true');
                }

                // Add to stack
                $stack.append($newCard);

                // Track placement during standard play
                if (window.gameMode === 'STANDARD_PLAY') {
                    const cardId = originalCardSrc.split('/').pop().replace('.svg', '').toUpperCase();
                    const isFaceUp = self.standardPlayInfluencePlacements.length > 0; // First card face down, second face up
                    
                    self.standardPlayInfluencePlacements.push({
                        cardId: cardId,
                        patricianType: patricianName,
                        faceUp: isFaceUp
                    });
                    
                    console.log('Standard play placement:', self.standardPlayInfluencePlacements);
                    
                    // Update action buttons based on cards placed
                    const buttons = [];
                    
                    if (self.standardPlayInfluencePlacements.length >= 1) {
                        buttons.push({
                            id: 'end-turn',
                            text: 'End Turn (Vote of Confidence)',
                            cssClass: 'primary-button'
                        });
                        
                        // Allow action card selection if cards have been placed
                        buttons.push({
                            id: 'play-action-card',
                            text: 'Play Action Card',
                            cssClass: 'secondary-button'
                        });
                    }
                    
                    if (buttons.length > 0) {
                        self.renderActionButtons(buttons);
                    }
                }

                // Set up hover functionality for newly placed face-down cards
                if (isFaceDown) {
                    setTimeout(() => {
                        self.setupFaceDownCardHover();
                    }, 50);
                }

                console.log('Calling updateSubmitButton after drop');
                window.GameUtils.updateSubmitButton();
            }
        });

        // Clicking a card in the influence area returns it to the player's hand
        $('#patrician-board-area').off('click', '.player-influence .influence-card').on('click', '.player-influence .influence-card', function (e) {
            if (window.gameMode === 'INITIAL_INFLUENCE_PLACEMENT' && window.waitingForInitialInfluence) {
                e.stopPropagation();
                console.log('Card clicked in influence area');
                const $card = $(this);
                const $stack = $card.closest('.influence-stack');
                
                // Create a new draggable card for the hand
                const $newCard = $('<img>');
                
                // Use the actual card if it's face down, or the current src if face up
                const actualCard = $card.attr('data-actual-card');
                const cardSrc = actualCard || $card.attr('src');
                
                $newCard.attr('src', cardSrc);
                $newCard.attr('alt', 'Influence');
                $newCard.attr('width', '100');
                $newCard.attr('height', '300');
                
                // Add to hand and make draggable
                $('#hand-area').append($newCard);
                window.GameUtils.makeDraggable($newCard);
                
                // Remove card from stack
                $card.remove();
                
                // Remove empty stack
                if ($stack.find('.influence-card').length === 0) {
                    $stack.remove();
                }
                
                console.log('Calling updateSubmitButton after click');
                window.GameUtils.updateSubmitButton();
            } else if (window.gameMode === 'STANDARD_PLAY') {
                e.stopPropagation();
                console.log('Standard play card clicked in influence area');
                const $card = $(this);
                const $stack = $card.closest('.influence-stack');
                const patricianName = $card.closest('.patrician-column').find('h3').text().trim().toUpperCase();
                
                // Find and remove this placement from our tracking
                const cardSrc = $card.attr('src');
                const cardId = cardSrc.split('/').pop().replace('.svg', '').toUpperCase();
                
                self.standardPlayInfluencePlacements = self.standardPlayInfluencePlacements.filter(p =>
                    !(p.cardId === cardId && p.patricianType === patricianName)
                );
                
                // Create a new draggable card for the hand
                const $newCard = $('<img>')
                    .attr('src', $card.attr('src'))
                    .attr('alt', 'Influence')
                    .attr('width', '100')
                    .attr('height', '300')
                    .css({
                        position: 'relative',
                        cursor: 'grab'
                    });
                
                // Add to hand and make draggable
                $('#hand-area').append($newCard);
                window.GameUtils.makeDraggable($newCard);
                
                // Remove card from stack
                $card.remove();
                
                // Remove empty stack
                if ($stack.find('.influence-card').length === 0) {
                    $stack.remove();
                }
                
                // Refresh action buttons
                self.refreshGameState();
                
                console.log('Updated standard play placements:', self.standardPlayInfluencePlacements);
            }
        });
    },

    setupInfluenceStackHover: function() {
        // Enhanced hover behavior for influence stacks
        // Use event delegation to handle dynamically created stacks
        $('#patrician-board-area').off('mousemove.stackhover mouseleave.stackhover', '.influence-stack');
        
        $('#patrician-board-area').on('mousemove.stackhover', '.influence-stack', function(e) {
            const $stack = $(this);
            const $cards = $stack.find('.influence-card');
            
            if ($cards.length <= 1) return; // No need for special behavior with single card
            
            const stackOffset = $stack.offset();
            const stackHeight = $stack.height();
            const relativeY = e.pageY - stackOffset.top;
            const normalizedY = Math.max(0, Math.min(1, relativeY / stackHeight));
            
            // Determine which card should be on top based on mouse position
            const cardIndex = Math.floor(normalizedY * $cards.length);
            const clampedIndex = Math.max(0, Math.min($cards.length - 1, cardIndex));
            
            // Reset all cards to their default state
            $cards.each(function(index) {
                const defaultZIndex = $cards.length - index;
                $(this).css({
                    'z-index': defaultZIndex,
                    'box-shadow': '0 2px 4px rgba(0,0,0,0.2)'
                });
            });
            
            // Bring the target card to the front with enhanced shadow
            $cards.eq(clampedIndex).css({
                'z-index': 25,
                'box-shadow': '0 4px 8px rgba(0,0,0,0.3)'
            });
        });
        
        $('#patrician-board-area').on('mouseleave.stackhover', '.influence-stack', function() {
            const $stack = $(this);
            const $cards = $stack.find('.influence-card');
            
            // Reset all cards to their default state when mouse leaves
            $cards.each(function(index) {
                const defaultZIndex = $cards.length - index;
                $(this).css({
                    'z-index': defaultZIndex,
                    'box-shadow': '0 2px 4px rgba(0,0,0,0.2)'
                });
            });
        });
    },

    setupFaceDownCardHover: function() {
        // Set up hover functionality for face-down cards on current player's side
        $('#patrician-board-area').off('mouseenter.facedown mouseleave.facedown', '.player-influence .influence-card[data-face-up="false"]');
        
        $('#patrician-board-area').on('mouseenter.facedown', '.player-influence .influence-card[data-face-up="false"]', function() {
            const $card = $(this);
            const actualCardSrc = $card.attr('data-actual-card');
            if (actualCardSrc) {
                $card.attr('src', actualCardSrc);
            }
        });
        
        $('#patrician-board-area').on('mouseleave.facedown', '.player-influence .influence-card[data-face-up="false"]', function() {
            const $card = $(this);
            // Return to showing the card back
            const cardBack = 'cards/backs/' + window.currentPlayer.toLowerCase() + '.svg';
            $card.attr('src', cardBack);
        });
    },

    bindEventHandlers: function() {
        const self = this;
        
        // Reset game functionality (persistent button)
        $('#reset-game').on('click', function () {
            if (confirm('Are you sure you want to reset the game?')) {
                axios.post('/game/reset')
                    .then((response) => {
                        console.log('Game reset:', response.data);
                        self.refreshGameState();
                        alert('Game has been reset!');
                    })
                    .catch(err => {
                        console.error('Failed to reset game', err);
                        alert('Failed to reset game');
                    });
            }
        });

        // Global mouse wheel handler for patrician board scrolling
        $(document).on('wheel', function(e) {
            const $patricianBoard = $('.patrician-board');
            if ($patricianBoard.length) {
                // Get the scroll element (the patrician board container)
                const scrollElement = $patricianBoard[0];
                
                // Check if the element is scrollable
                if (scrollElement.scrollHeight > scrollElement.clientHeight) {
                    // Calculate scroll amount (deltaY is negative for scroll up, positive for scroll down)
                    const scrollAmount = e.originalEvent.deltaY;
                    
                    // Scroll the patrician board
                    scrollElement.scrollTop += scrollAmount;
                    
                    // Prevent default browser scroll behavior
                    e.preventDefault();
                    return false;
                }
            }
        });
    },

    bindActionButtonEvents: function() {
        const self = this;
        
        // Handle submit initial influence
        $(document).off('click', '#submit-influence').on('click', '#submit-influence', function () {
            if (!window.waitingForInitialInfluence || window.gameMode !== 'INITIAL_INFLUENCE_PLACEMENT') {
                alert('Not your turn or not in initial influence placement mode');
                return;
            }

            const placements = {};
            $('.patrician-column').each(function () {
                const patrician = $(this).find('h3').text().trim().toUpperCase();
                const $card = $(this).find('.player-influence .influence-card').first();
                if ($card.length) {
                    // Use the actual card data if it's face down, otherwise use the visible card
                    const actualCard = $card.attr('data-actual-card');
                    const imgSrc = actualCard || $card.attr('src');
                    const card = imgSrc.split('/').pop().replace('.svg', '').toUpperCase();
                    placements[patrician] = card;
                }
            });

            axios.post(`/game/action/placeInitialInfluence?playerId=${window.currentPlayer}`, placements)
                .then((response) => {
                    console.log('Initial influence submitted:', response.data);
                    
                    // Refresh the game state to show the next player's turn or transition to standard play
                    self.refreshGameState();
                    
                    if (response.data.currentMode === 'STANDARD_PLAY') {
                        alert('Both players have placed initial influence. Game enters standard play mode!');
                    } else {
                        alert(`${window.currentPlayer} initial influence submitted. Now ${response.data.currentPlayer}'s turn.`);
                    }
                })
                .catch(err => {
                    console.error('Failed to submit initial influence', err);
                    if (err.response && err.response.data && err.response.data.error) {
                        alert('Error: ' + err.response.data.error);
                    } else {
                        alert('Failed to submit initial influence');
                    }
                });
        });

        // Handle End Turn button for standard play - submit influence cards and initiate VOC
        $(document).off('click', '#end-turn').on('click', '#end-turn', function() {
            if (window.gameMode !== 'STANDARD_PLAY') {
                alert('End turn is only available during standard play');
                return;
            }
            
            if (self.standardPlayInfluencePlacements.length === 0) {
                alert('You must place at least one influence card before ending turn');
                return;
            }
            
            // Store scroll position before submission
            const scrollPos = $('.patrician-board')[0].scrollTop;
            // Submit influence cards and then initiate VOC
            self.submitInfluenceCardsAndEndTurn(scrollPos);
        });
        
        // Handle Play Action Card button
        $(document).off('click', '#play-action-card').on('click', '#play-action-card', function() {
            if (window.gameMode !== 'STANDARD_PLAY') {
                alert('Action cards can only be played during standard play');
                return;
            }
            
            if (self.standardPlayInfluencePlacements.length === 0) {
                alert('You must place at least one influence card before playing an action card');
                return;
            }
            
            // Submit influence cards first, then show action card selection
            self.submitInfluenceCardsAndShowActionSelection();
        });
        
        // Handle Skip to Action Phase button (for when no influence cards are placed)
        $(document).off('click', '#skip-to-action').on('click', '#skip-to-action', function() {
            if (window.gameMode !== 'STANDARD_PLAY') {
                alert('Skip to action is only available during standard play');
                return;
            }
            
            // Show action card selection without placing influence cards
            self.showActionCardSelection();
        });
        
        // Handle Auto-Complete Initial Influence button (for testing)
        $(document).off('click', '#auto-complete-influence').on('click', '#auto-complete-influence', function() {
            if (!window.waitingForInitialInfluence || window.gameMode !== 'INITIAL_INFLUENCE_PLACEMENT') {
                alert('Auto-complete is only available during initial influence placement');
                return;
            }
            
            self.autoCompleteInitialInfluence();
        });
    },
    
    
    // Submit influence cards and then end turn with VOC
    submitInfluenceCardsAndEndTurn: function(scrollPos) {
        const self = this;
        
        // First submit influence cards
        self.submitInfluenceCardsOnly(() => {
            // After influence cards are submitted, initiate Vote of Confidence
            // For now, we'll prompt user to select patrician type for VOC
            const patricianTypes = ['QUAESTOR', 'AEDILE', 'PRAETOR', 'CONSUL', 'CENSOR'];
            const selectedPatrician = prompt('Select patrician type for Vote of Confidence:\n' +
                patricianTypes.map((p, i) => `${i+1}. ${p}`).join('\n'));
            
            if (selectedPatrician) {
                const index = parseInt(selectedPatrician) - 1;
                if (index >= 0 && index < patricianTypes.length) {
                    self.executeVoteOfConfidence(patricianTypes[index], scrollPos);
                } else {
                    alert('Invalid selection');
                }
            }
        });
    },
    
    // Submit influence cards and show action card selection
    submitInfluenceCardsAndShowActionSelection: function() {
        const self = this;
        
        // First submit influence cards
        self.submitInfluenceCardsOnly(() => {
            // After influence cards are submitted, show action card selection
            self.showActionCardSelection();
        });
    },
    
    // Submit influence cards only (without ending turn)
    submitInfluenceCardsOnly: function(onComplete) {
        const self = this;
        
        if (self.standardPlayInfluencePlacements.length === 0) {
            console.log('No influence cards to submit');
            if (onComplete) onComplete();
            return;
        }
        
        const payload = {
            playerId: window.currentPlayer
        };
        
        // Set up face down assignment (first card)
        if (self.standardPlayInfluencePlacements.length >= 1) {
            const faceDownCard = self.standardPlayInfluencePlacements.find(p => !p.faceUp);
            if (faceDownCard) {
                payload.faceDownAssignment = {
                    influenceCardId: faceDownCard.cardId,
                    patricianType: faceDownCard.patricianType
                };
            }
        }
        
        // Set up face up assignment (second card)
        if (self.standardPlayInfluencePlacements.length >= 2) {
            const faceUpCard = self.standardPlayInfluencePlacements.find(p => p.faceUp);
            if (faceUpCard) {
                payload.faceUpAssignment = {
                    influenceCardId: faceUpCard.cardId,
                    patricianType: faceUpCard.patricianType
                };
            }
        }
        
        console.log('Submitting influence cards:', payload);
        
        axios.post('/game/action/playInfluenceCard', payload)
            .then((response) => {
                console.log('Influence cards submitted:', response.data);
                
                // Clear placements
                self.standardPlayInfluencePlacements = [];
                
                if (onComplete) onComplete();
            })
            .catch(err => {
                console.error('Failed to submit influence cards', err);
                if (err.response && err.response.data && err.response.data.error) {
                    alert('Error: ' + err.response.data.error);
                } else {
                    alert('Failed to submit influence cards');
                }
            });
    },
    
    // Execute Vote of Confidence
    executeVoteOfConfidence: function(patricianType, scrollPos) {
        const self = this;
        
        const payload = {
            playerId: window.currentPlayer,
            patricianType: patricianType
        };
        
        axios.post('/game/action/voteOfConfidence', payload)
            .then((response) => {
                console.log('Vote of Confidence executed:', response.data);
                
                // Refresh game state
                self.refreshGameState();
                
                // Restore scroll position
                if (scrollPos !== undefined) {
                    setTimeout(() => {
                        const $patricianBoard = $('.patrician-board');
                        if ($patricianBoard.length) {
                            $patricianBoard[0].scrollTop = scrollPos;
                        }
                    }, 100);
                }
                
                if (response.data.winner) {
                    alert(`Vote of Confidence completed! Winner: ${response.data.winner}`);
                } else {
                    alert('Vote of Confidence executed successfully!');
                }
            })
            .catch(err => {
                console.error('Failed to execute Vote of Confidence', err);
                if (err.response && err.response.data && err.response.data.error) {
                    alert('Error: ' + err.response.data.error);
                } else {
                    alert('Failed to execute Vote of Confidence');
                }
            });
    },
    
    // Show action card selection (placeholder for now)
    showActionCardSelection: function() {
        // For now, just show available action cards in an alert
        // This should be replaced with a proper UI for action card selection
        alert('Action card selection not yet implemented.\nAvailable cards will be shown here for selection.');
        
        // Refresh game state after showing action selection
        this.refreshGameState();
    },
    
    // Auto-complete initial influence placement for testing purposes
    autoCompleteInitialInfluence: function() {
        const self = this;
        
        // Clear any existing placements first
        $('.player-influence .influence-stack').remove();
        
        // Return all cards to hand first and update submit button
        $('#hand-area').empty();
        const handCards = ['influence/one.svg', 'influence/two.svg', 'influence/three.svg', 'influence/four.svg', 'influence/five.svg'];
        handCards.forEach(cardSrc => {
            const $card = $('<img>')
                .attr('src', 'cards/' + cardSrc)
                .attr('alt', 'Influence')
                .attr('width', '100')
                .attr('height', '300');
            $('#hand-area').append($card);
            window.GameUtils.makeDraggable($card);
        });
        
        // Update submit button after clearing and repopulating hand
        setTimeout(() => {
            window.GameUtils.updateSubmitButton();
            
            // Auto-place cards on patricians (one card per patrician)
            const patricianColumns = $('.patrician-column');
            const cardsToPlace = handCards.slice(0, Math.min(handCards.length, patricianColumns.length));
            
            cardsToPlace.forEach((cardSrc, index) => {
                if (index < patricianColumns.length) {
                    const $column = $(patricianColumns[index]);
                    const $playerInfluence = $column.find('.player-influence');
                    
                    // Create influence stack if it doesn't exist
                    let $stack = $playerInfluence.find('.influence-stack');
                    if ($stack.length === 0) {
                        $stack = $('<div class="influence-stack"></div>');
                        $playerInfluence.append($stack);
                    }
                    
                    // Create the card element (face down)
                    const $newCard = $('<img>')
                        .attr('src', 'cards/backs/' + window.currentPlayer.toLowerCase() + '.svg')
                        .attr('alt', 'Influence')
                        .attr('width', '100')
                        .attr('height', '300')
                        .addClass('influence-card')
                        .attr('data-actual-card', 'cards/' + cardSrc)
                        .attr('data-face-up', 'false')
                        .css({
                            position: 'absolute',
                            top: '0px',
                            left: '0px',
                            opacity: '1'
                        });
                    
                    $stack.append($newCard);
                    
                    // Remove card from hand immediately after placing
                    const $handCard = $('#hand-area img').filter(`[src="cards/${cardSrc}"]`).first();
                    $handCard.remove();
                }
            });
            
            // Set up hover functionality for the newly placed cards and update submit button
            setTimeout(() => {
                self.setupFaceDownCardHover();
                // Final update of submit button after all cards are placed
                window.GameUtils.updateSubmitButton();
            }, 50);
            
        }, 50);
    }
};