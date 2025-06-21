// Game initialization and utility functions
$(function () {
    // Register Handlebars helper for greater than comparison
    Handlebars.registerHelper('gt', function(a, b) {
        return a > b;
    });
    
    function capitalize(str) {
        return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
    }
    
    function cardFileName(type) {
        return type.toLowerCase() + '.svg';
    }
    
    function makeDraggable($imgs) {
        $imgs.draggable({
            revert: 'invalid',
            helper: 'clone',
            start: function () {
                console.log('Drag started');
                $(this).css('opacity', 0.5);
            },
            stop: function () {
                console.log('Drag stopped');
                $(this).css('opacity', 1);
            }
        });
    }
    
    function updateSubmitButton() {
        // Use setTimeout to ensure DOM has been updated
        setTimeout(function() {
            const handCount = $('#hand-area img').length;
            const placedCount = $('.player-influence .influence-card').length;
            console.log('Hand count:', handCount, 'Placed count:', placedCount);
            
            // Enable button when all 5 cards are placed (hand is empty)
            $('#submit-influence').prop('disabled', handCount !== 0);
        }, 10);
    }
    
    function createInfluenceStack($container, cards) {
        if (cards.length === 0) return;
        
        const $stack = $('<div class="influence-stack"></div>');
        
        cards.forEach(function(cardSrc) {
            const $card = $('<img>');
            $card.attr('src', 'cards/' + cardSrc);
            $card.attr('alt', 'Influence');
            $card.attr('width', '100');
            $card.attr('height', '300');
            $card.addClass('influence-card');
            $stack.append($card);
        });
        
        $container.append($stack);
    }

    // Expose utility functions globally for use in game.js
    window.GameUtils = {
        capitalize: capitalize,
        cardFileName: cardFileName,
        makeDraggable: makeDraggable,
        updateSubmitButton: updateSubmitButton,
        createInfluenceStack: createInfluenceStack
    };

    // Initialize the game
    window.GameCore.init();
});