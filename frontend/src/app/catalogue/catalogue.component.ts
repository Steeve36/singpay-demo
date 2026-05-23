import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

export interface Product {
  id: string;
  name: string;
  price: number;
  period: string | null;
  description: string;
  features: string[];
  badge: string | null;
  highlight: boolean;
}

@Component({
  selector: 'app-catalogue',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './catalogue.component.html',
  styleUrls: ['./catalogue.component.scss']
})
export class CatalogueComponent {

  forfaits: Product[] = [
    {
      id: 'starter',
      name: 'Forfait Starter',
      price: 5000,
      period: '/mois',
      description: 'Pour démarrer en solo ou en petite équipe.',
      features: [
        '1 utilisateur',
        '5 Go de stockage',
        'Tableau de bord basique',
        'Support par e-mail',
      ],
      badge: null,
      highlight: false,
    },
    {
      id: 'pro',
      name: 'Forfait Pro',
      price: 15000,
      period: '/mois',
      description: 'La solution complète pour les équipes en croissance.',
      features: [
        '5 utilisateurs',
        '50 Go de stockage',
        'Tableau de bord avancé',
        'API Access',
        'Support prioritaire 7j/7',
      ],
      badge: 'Populaire',
      highlight: true,
    },
    {
      id: 'business',
      name: 'Forfait Business',
      price: 35000,
      period: '/mois',
      description: 'Pour les entreprises avec des besoins avancés.',
      features: [
        'Utilisateurs illimités',
        '500 Go de stockage',
        'Toutes les fonctionnalités Pro',
        'Personnalisation avancée',
        'Support dédié 24h/24',
      ],
      badge: null,
      highlight: false,
    },
  ];

  services: Product[] = [
    {
      id: 'consultation',
      name: 'Consultation Expert',
      price: 10000,
      period: null,
      description: 'Une session individuelle avec un expert pour analyser vos besoins.',
      features: [
        'Session de 1h en visio',
        'Analyse de vos besoins',
        'Recommandations personnalisées',
        'Compte-rendu écrit',
      ],
      badge: null,
      highlight: false,
    },
    {
      id: 'formation',
      name: 'Formation Complète',
      price: 25000,
      period: null,
      description: 'Maîtrisez la plateforme de A à Z avec nos formateurs.',
      features: [
        'Formation de 8h (2 jours)',
        'Supports de cours inclus',
        'Accès aux replays à vie',
        'Certificat de complétion',
      ],
      badge: 'Nouveau',
      highlight: false,
    },
    {
      id: 'audit',
      name: 'Audit & Intégration',
      price: 50000,
      period: null,
      description: 'Déploiement clé en main et intégration dans votre système existant.',
      features: [
        'Audit de l\'existant',
        'Plan de migration',
        'Intégration technique',
        'Formation des équipes',
        'Suivi post-déploiement (1 mois)',
      ],
      badge: null,
      highlight: false,
    },
  ];

  constructor(private router: Router) {}

  choisir(product: Product): void {
    this.router.navigate(['/checkout'], {
      queryParams: {
        product: product.name,
        amount: product.price,
      }
    });
  }

  formatPrice(price: number): string {
    return price.toLocaleString('fr-FR') + ' FCFA';
  }
}
