// ============ USER PROFILE ENHANCEMENT JAVASCRIPT ============

/**
 * Enhanced User Profile Interactive Features
 * Supports: Password Strength, Progress Tracking, Animations, Avatar Preview
 */

class UserProfileEnhancer {
    constructor() {
        this.progressSteps = ['basic-info', 'avatar', 'security'];
        this.currentStep = 0;
        this.init();
    }

    init() {
        console.log('🚀 UserProfileEnhancer initialized');
        this.setupEventListeners();
        this.initializeAnimations();
    }

    setupEventListeners() {
        // Wait for DOM to be ready
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => this.attachListeners());
        } else {
            this.attachListeners();
        }
    }

    attachListeners() {
        // Avatar hover effects
        this.setupAvatarInteractions();
        
        // Form field animations
        this.setupFormAnimations();
        
        // Card hover effects
        this.setupCardAnimations();
    }

    setupAvatarInteractions() {
        const avatarContainer = document.querySelector('.avatar-preview-modern');
        const avatarOverlay = document.querySelector('.avatar-overlay');
        
        if (avatarContainer && avatarOverlay) {
            avatarContainer.addEventListener('mouseenter', () => {
                avatarOverlay.style.transform = 'scale(1.1)';
                avatarOverlay.style.boxShadow = '0 4px 16px rgba(102, 126, 234, 0.3)';
            });
            
            avatarContainer.addEventListener('mouseleave', () => {
                avatarOverlay.style.transform = 'scale(1)';
                avatarOverlay.style.boxShadow = '0 2px 8px rgba(0, 0, 0, 0.2)';
            });
        }
    }

    setupFormAnimations() {
        const inputs = document.querySelectorAll('.modern-input, .password-input');
        inputs.forEach(input => {
            input.addEventListener('focus', (e) => {
                this.animateInputFocus(e.target);
            });
            
            input.addEventListener('blur', (e) => {
                this.animateInputBlur(e.target);
            });
        });
    }

    setupCardAnimations() {
        const cards = document.querySelectorAll('.profile-card');
        cards.forEach((card, index) => {
            card.style.animationDelay = `${index * 0.1}s`;
            
            card.addEventListener('mouseenter', () => {
                card.style.transform = 'translateY(-4px)';
                card.style.boxShadow = '0 12px 40px rgba(0, 0, 0, 0.15)';
            });
            
            card.addEventListener('mouseleave', () => {
                card.style.transform = 'translateY(0)';
                card.style.boxShadow = '0 4px 20px rgba(0, 0, 0, 0.08)';
            });
        });
    }

    animateInputFocus(input) {
        input.style.transform = 'translateY(-2px)';
        input.style.boxShadow = '0 6px 20px rgba(102, 126, 234, 0.2)';
    }

    animateInputBlur(input) {
        input.style.transform = 'translateY(0)';
        input.style.boxShadow = 'none';
    }

    initializeAnimations() {
        // Stagger card animations
        const cards = document.querySelectorAll('.profile-card');
        cards.forEach((card, index) => {
            setTimeout(() => {
                card.style.opacity = '1';
                card.style.transform = 'translateY(0)';
            }, index * 150);
        });
    }
}

// ============ GLOBAL FUNCTIONS FOR JAVA INTEGRATION ============

/**
 * Show password change form with animation (Jmix visibility handled in Java)
 */
window.showPasswordChangeForm = function() {
    const container = document.getElementById('passwordChangeContainer');
    
    if (container) {
        // Add smooth transition animation
        container.style.opacity = '0';
        container.style.transform = 'translateY(20px)';
        
        setTimeout(() => {
            container.style.transition = 'all 0.3s ease';
            container.style.opacity = '1';
            container.style.transform = 'translateY(0)';
        }, 10);
    }
};

/**
 * Hide password change form
 */
window.hidePasswordChangeForm = function() {
    const container = document.getElementById('passwordChangeContainer');
    if (container) {
        container.style.opacity = '0';
        container.style.transform = 'translateY(-20px)';
        setTimeout(() => {
            container.style.display = 'none';
        }, 300);
    }
};

/**
 * Update password strength indicator (visibility handled by Java)
 */
window.updatePasswordStrength = function(strength, strengthText, strengthColor) {
    const progressBar = document.getElementById('strengthProgress');
    const strengthTextElement = document.getElementById('strengthText');
    const indicator = document.getElementById('passwordStrengthIndicator');
    
    if (progressBar && strengthTextElement) {
        progressBar.style.width = strength + '%';
        progressBar.style.background = `linear-gradient(90deg, ${strengthColor}, ${adjustBrightness(strengthColor, 20)})`;
        strengthTextElement.textContent = strengthText;
        strengthTextElement.style.color = strengthColor;
        
        if (indicator) {
            // Add fadeIn animation for visible indicator
            indicator.style.animation = 'fadeInUp 0.3s ease';
        }
        
        // Add pulse effect for strong passwords
        if (strength >= 80) {
            progressBar.style.animation = 'pulse 2s infinite';
        } else {
            progressBar.style.animation = 'none';
        }
    }
};

/**
 * Hide password strength indicator (handled by Java setVisible())
 * This function is kept for compatibility but visibility is managed in Java
 */
window.hidePasswordStrength = function() {
    // Visibility is now handled by Java using setVisible(false)
    // This function is kept for JavaScript compatibility
};

/**
 * Update password requirements checklist
 */
window.updatePasswordRequirements = function(lengthOk, upperOk, numberOk) {
    const lengthIcon = document.getElementById('lengthCheck');
    const upperIcon = document.getElementById('upperCheck');
    const numberIcon = document.getElementById('numberCheck');
    
    updateRequirementIcon(lengthIcon, lengthOk);
    updateRequirementIcon(upperIcon, upperOk);
    updateRequirementIcon(numberIcon, numberOk);
};

function updateRequirementIcon(icon, satisfied) {
    if (icon) {
        if (satisfied) {
            icon.setAttribute('icon', 'CHECK_CIRCLE');
            icon.classList.add('satisfied');
            icon.style.color = '#28a745';
            icon.style.animation = 'pulse 0.3s ease';
        } else {
            icon.setAttribute('icon', 'CIRCLE');
            icon.classList.remove('satisfied');
            icon.style.color = '#dc3545';
            icon.style.animation = 'none';
        }
    }
}

/**
 * Show password match validation
 */
window.showPasswordMatchValidation = function(matches) {
    const confirmField = document.getElementById('confirmPasswordField');
    if (confirmField) {
        if (matches) {
            confirmField.style.borderColor = '#28a745';
            confirmField.style.boxShadow = '0 0 0 3px rgba(40, 167, 69, 0.1)';
            showValidationMessage(confirmField, '✓ Mật khẩu khớp', '#28a745');
        } else {
            confirmField.style.borderColor = '#dc3545';
            confirmField.style.boxShadow = '0 0 0 3px rgba(220, 53, 69, 0.1)';
            showValidationMessage(confirmField, '✗ Mật khẩu không khớp', '#dc3545');
        }
    }
};

function showValidationMessage(field, message, color) {
    let validationMsg = field.parentNode.querySelector('.validation-message');
    if (!validationMsg) {
        validationMsg = document.createElement('div');
        validationMsg.className = 'validation-message';
        validationMsg.style.cssText = `
            font-size: 12px;
            margin-top: 4px;
            font-weight: 500;
            transition: all 0.3s ease;
        `;
        field.parentNode.appendChild(validationMsg);
    }
    
    validationMsg.textContent = message;
    validationMsg.style.color = color;
    validationMsg.style.opacity = '1';
}

/**
 * Update progress steps
 */
window.updateProgressStep = function(step) {
    const steps = document.querySelectorAll('.progress-step');
    const lines = document.querySelectorAll('.progress-line');
    
    steps.forEach((stepElement, index) => {
        if (index < step) {
            stepElement.classList.add('completed');
            stepElement.classList.remove('active');
        } else if (index === step - 1) {
            stepElement.classList.add('active');
            stepElement.classList.remove('completed');
        } else {
            stepElement.classList.remove('active', 'completed');
        }
    });
    
    lines.forEach((line, index) => {
        if (index < step - 1) {
            line.classList.add('completed');
        } else {
            line.classList.remove('completed');
        }
    });
};

/**
 * Complete all progress steps
 */
window.completeAllProgressSteps = function() {
    const steps = document.querySelectorAll('.progress-step');
    const lines = document.querySelectorAll('.progress-line');
    
    steps.forEach(step => {
        step.classList.add('completed');
        step.classList.remove('active');
    });
    
    lines.forEach(line => {
        line.classList.add('completed');
    });
    
    // Add celebration animation
    setTimeout(() => {
        steps.forEach((step, index) => {
            setTimeout(() => {
                step.style.animation = 'pulse 0.6s ease';
            }, index * 100);
        });
    }, 200);
};

/**
 * Initialize progress steps
 */
window.initializeProgressSteps = function() {
    updateProgressStep(1); // Start with step 1 active
};

/**
 * Display avatar image from URL
 */
window.displayAvatarImage = function(imageUrl) {
    const avatarContainer = document.getElementById('avatarImageDisplay');
    if (avatarContainer && imageUrl) {
        // Clear existing content
        avatarContainer.innerHTML = '';
        
        // Create image element
        const img = document.createElement('img');
        img.src = imageUrl;
        img.style.cssText = `
            width: 100%;
            height: 100%;
            object-fit: cover;
            border-radius: 50%;
        `;
        
        // Handle image load error
        img.onerror = function() {
            avatarContainer.innerHTML = '<div class="gov-avatar-placeholder"><i class="vaadin-icon vaadin-icon-user"></i></div>';
        };
        
        // Add loading animation
        avatarContainer.style.opacity = '0.5';
        
        img.onload = function() {
            avatarContainer.style.opacity = '1';
            avatarContainer.style.animation = 'successPulse 0.6s ease';
        };
        
        avatarContainer.appendChild(img);
    }
};

/**
 * Update avatar preview (legacy function for compatibility)
 */
window.updateAvatarPreview = function() {
    const avatarContainer = document.getElementById('avatarImageDisplay');
    if (avatarContainer) {
        avatarContainer.style.animation = 'successPulse 0.6s ease';
        
        // Add success indicator
        setTimeout(() => {
            const successIcon = document.createElement('div');
            successIcon.innerHTML = '✓';
            successIcon.style.cssText = `
                position: absolute;
                top: 50%;
                left: 50%;
                transform: translate(-50%, -50%);
                background: #28a745;
                color: white;
                border-radius: 50%;
                width: 40px;
                height: 40px;
                display: flex;
                align-items: center;
                justify-content: center;
                font-weight: bold;
                animation: fadeInUp 0.3s ease;
                z-index: 10;
            `;
            avatarContainer.appendChild(successIcon);
            
            setTimeout(() => {
                successIcon.remove();
            }, 2000);
        }, 300);
    }
};

/**
 * Show save success animation
 */
window.showSaveSuccessAnimation = function() {
    const saveButton = document.getElementById('saveAndCloseButton');
    if (saveButton) {
        saveButton.style.animation = 'successPulse 0.6s ease';
        
        // Create success overlay
        const overlay = document.createElement('div');
        overlay.style.cssText = `
            position: fixed;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            background: rgba(40, 167, 69, 0.95);
            color: white;
            padding: 20px 40px;
            border-radius: 12px;
            font-size: 18px;
            font-weight: 600;
            z-index: 10000;
            animation: fadeInUp 0.5s ease;
            box-shadow: 0 8px 32px rgba(40, 167, 69, 0.3);
        `;
        overlay.textContent = '🎉 Cập nhật thành công!';
        document.body.appendChild(overlay);
        
        setTimeout(() => {
            overlay.style.opacity = '0';
            setTimeout(() => overlay.remove(), 300);
        }, 2000);
    }
};

// ============ UTILITY FUNCTIONS ============

function adjustBrightness(color, percent) {
    const num = parseInt(color.replace('#', ''), 16);
    const amt = Math.round(2.55 * percent);
    const R = (num >> 16) + amt;
    const G = (num >> 8 & 0x00FF) + amt;
    const B = (num & 0x0000FF) + amt;
    return '#' + (0x1000000 + (R < 255 ? R < 1 ? 0 : R : 255) * 0x10000 +
        (G < 255 ? G < 1 ? 0 : G : 255) * 0x100 +
        (B < 255 ? B < 1 ? 0 : B : 255)).toString(16).slice(1);
}

// ============ INITIALIZATION ============

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        new UserProfileEnhancer();
    });
} else {
    new UserProfileEnhancer();
}

// Export for module usage
if (typeof module !== 'undefined' && module.exports) {
    module.exports = UserProfileEnhancer;
}
