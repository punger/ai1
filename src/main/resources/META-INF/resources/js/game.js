// Main game logic and state management
window.GameCore = {
    
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
                        // Show submit initial influence button
                        self.renderActionButtons([{
                            id: 'submit-influence',
                            text: 'Submit Initial Influence',
                            cssClass: 'primary-button',
                            disabled: true
                        }]);
                    } else {
                        $('#turn-info').text(`Waiting for ${current} to place initial influence`);
                        self.clearActionButtons();
                    }
                } else if (gameMode === 'STANDARD_PLAY') {
                    $('#game-status').text(`Standard Play - ${current}'s turn`);
                    $('#turn-info').text('');
                    // Future: Add standard play action buttons here
                    self.clearActionButtons();
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
                    
                    // Process current player influence cards - show backs when face down
                    const playerInf = (entry.influence[current] || []).map(i => {
                        if (i.faceUp) {
                            return 'influence/' + window.GameUtils.cardFileName(i.type);
                        } else {
                            // Show appropriate card back for current player
                            return 'backs/' + current.toLowerCase() + '.svg';
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
                        self.setupDragAndDrop();
                        self.setupInfluenceStackHover();
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
        this.loadTemplate('templates/hand-card.hbs')
            .then(function(templateSource) {
                const handTemplate = Handlebars.compile(templateSource);
                $('#hand-area').html(handTemplate({ hand: handCards }));
                
                // Enable drag-and-drop for influence cards in hand
                window.GameUtils.makeDraggable($('#hand-area img'));
                
                // During standard play, also make the hand cards clickable for additional interactions
                if (window.gameMode === 'STANDARD_PLAY') {
                    $('#hand-area img').css('cursor', 'grab');
                }
            });
    },

    setupDragAndDrop: function() {
        // Make current player's influence zones accept dropped cards
        $('.player-influence').droppable({
            accept: '#hand-area img',
            hoverClass: 'droppable-hover',
            drop: function (event, ui) {
                console.log('Card dropped');
                const $target = $(this);
                const $card = $(ui.draggable);

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
                        $existing.detach().appendTo('#hand-area');
                        window.GameUtils.makeDraggable($existing);
                    }
                    
                    // Clear the stack and add new card
                    $stack.empty();
                }
                // During standard play, just add the card to the stack (no replacement)

                // Create the new card element
                const $newCard = $card.clone();
                $newCard.removeClass('ui-draggable ui-draggable-handle')
                       .css({ top: '', left: '', position: 'absolute', opacity: '1' })
                       .addClass('influence-card');

                // Add to stack
                $stack.append($newCard);

                // Remove original card from hand
                $card.remove();

                console.log('Calling updateSubmitButton after drop');
                window.GameUtils.updateSubmitButton();
            }
        });

        // Clicking a card in the influence area returns it to the player's hand (only during initial influence placement)
        $('#patrician-board-area').off('click', '.player-influence .influence-card').on('click', '.player-influence .influence-card', function (e) {
            if (window.gameMode === 'INITIAL_INFLUENCE_PLACEMENT' && window.waitingForInitialInfluence) {
                e.stopPropagation();
                console.log('Card clicked in influence area');
                const $card = $(this);
                const $stack = $card.closest('.influence-stack');
                
                // Create a new draggable card for the hand
                const $newCard = $('<img>');
                $newCard.attr('src', $card.attr('src'));
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
                    const imgSrc = $card.attr('src');
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

        // Future: Add other action button handlers here
        // Example:
        // $(document).off('click', '#play-action-card').on('click', '#play-action-card', function() { ... });
        // $(document).off('click', '#end-turn').on('click', '#end-turn', function() { ... });
    }
};